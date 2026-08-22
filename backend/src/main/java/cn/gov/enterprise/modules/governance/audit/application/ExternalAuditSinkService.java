package cn.gov.enterprise.modules.governance.audit.application;

import cn.gov.enterprise.modules.governance.audit.domain.ExternalAuditEvent;
import cn.gov.enterprise.modules.governance.audit.domain.ExternalAuditReceipt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Independent Governance context receiver. It does not call Workflow repositories. */
@Service
public class ExternalAuditSinkService {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final Clock clock;
    @Autowired public ExternalAuditSinkService(JdbcTemplate jdbc,ObjectMapper json){this(jdbc,json,Clock.systemUTC());}
    ExternalAuditSinkService(JdbcTemplate jdbc,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.json=json;this.clock=clock;}

    @Transactional
    public ExternalAuditReceipt receive(ExternalAuditEvent event){
        if(!event.verifyPayloadHash())throw new ExternalAuditRejection("PAYLOAD_HASH_MISMATCH");
        ExternalAuditReceipt existing=find(event.auditEventId());
        if(existing!=null){
            if(!existing.payloadHash().equals(event.payloadHash()))throw new ExternalAuditRejection("IDEMPOTENCY_CONFLICT");
            return existing;
        }
        Instant receivedAt=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        String receiptId="GAER-"+event.payloadHash().substring(0,32);
        var receipt=new ExternalAuditReceipt(event.auditEventId(),receiptId,"GOVERNANCE_AUDIT_SINK","1.0.0",
                receivedAt,event.payloadHash(),ExternalAuditReceipt.Status.ACCEPTED,null).withComputedHash();
        try{
            jdbc.update("""
                INSERT INTO governance_external_audit_event
                (audit_event_id,event_type,event_version,occurred_at,enterprise_id,workflow_instance_id,
                 node_execution_id,task_id,claim_id,candidate_user_id,runtime_binding_id,admission_id,
                 eligibility_evidence_id,directory_revision,directory_result_hash,candidate_pool_hash,
                 eligibility_hash,claim_audit_hash,correlation_id,payload_hash,event_payload,received_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),?)
                """,event.auditEventId(),event.eventType(),event.eventVersion(),Timestamp.from(event.occurredAt()),
                    event.enterpriseId(),event.workflowInstanceId(),event.nodeExecutionId(),event.taskId(),event.claimId(),
                    event.candidateUserId(),event.runtimeBindingId(),event.admissionId(),event.eligibilityEvidenceId(),
                    event.directoryRevision(),event.directoryResultHash(),event.candidatePoolHash(),event.eligibilityHash(),
                    event.claimAuditHash(),event.correlationId(),event.payloadHash(),json.writeValueAsString(event),Timestamp.from(receivedAt));
            jdbc.update("""
                INSERT INTO governance_external_audit_receipt
                (external_receipt_id,audit_event_id,provider_code,provider_version,received_at,payload_hash,receipt_status,receipt_hash)
                VALUES (?,?,?,?,?,?,?,?)
                """,receipt.externalReceiptId(),receipt.auditEventId(),receipt.providerCode(),receipt.providerVersion(),
                    Timestamp.from(receipt.receivedAt()),receipt.payloadHash(),receipt.receiptStatus().name(),receipt.receiptHash());
            return receipt;
        }catch(DuplicateKeyException race){
            ExternalAuditReceipt winner=find(event.auditEventId());
            if(winner!=null&&winner.payloadHash().equals(event.payloadHash()))return winner;
            throw new ExternalAuditRejection("IDEMPOTENCY_CONFLICT");
        }catch(ExternalAuditRejection e){throw e;}
        catch(Exception e){throw new IllegalStateException("external audit persistence failed",e);}
    }
    private ExternalAuditReceipt find(String eventId){
        List<ExternalAuditReceipt> rows=jdbc.query("""
            SELECT r.audit_event_id,r.external_receipt_id,r.provider_code,r.provider_version,r.received_at,
                   r.payload_hash,r.receipt_status,r.receipt_hash
            FROM governance_external_audit_receipt r WHERE r.audit_event_id=?
            """,(rs,n)->new ExternalAuditReceipt(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                rs.getTimestamp(5).toInstant(),rs.getString(6),ExternalAuditReceipt.Status.valueOf(rs.getString(7)),rs.getString(8)),eventId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public static final class ExternalAuditRejection extends RuntimeException { public ExternalAuditRejection(String message){super(message);} }
}
