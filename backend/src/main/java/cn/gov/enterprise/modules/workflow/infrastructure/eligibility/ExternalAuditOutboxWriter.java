package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaim;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.governance.audit.domain.ExternalAuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Writes the external-audit intent in the same transaction as the internal Claim audit. */
@Component
public final class ExternalAuditOutboxWriter {
    public static final String PROVIDER_CODE = "ROLE_RUNTIME_EXTERNAL_AUDIT";
    public static final String PROVIDER_VERSION = "V1";
    private final JdbcTemplate jdbc; private final WorkflowIdentityGenerator ids; private final ObjectMapper json;
    public ExternalAuditOutboxWriter(JdbcTemplate jdbc, WorkflowIdentityGenerator ids,ObjectMapper json){this.jdbc=jdbc;this.ids=ids;this.json=json;}

    public void enqueue(long claimAuditId, String eventHash, TaskClaim claim) {
        if (claim.eligibilityEvidenceId() == null) return; // USER/DIRECT/Legacy remain unchanged.
        long id=ids.nextId(); Instant now=Instant.now(); String eventId="ROLE-CLAIM-"+claimAuditId;
        try {
            EventSource source=jdbc.queryForObject("""
                SELECT CAST(i.enterprise_id AS CHAR),CAST(e.resolver_binding_id AS CHAR),
                       e.candidate_directory_revision,e.directory_result_hash,e.candidate_pool_hash
                FROM workflow_instance i JOIN workflow_role_realtime_eligibility_evidence e ON e.instance_id=i.id
                WHERE i.id=? AND e.id=? AND i.deleted=0 AND e.deleted=0
                """,(rs,n)->new EventSource(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),
                    claim.instanceId(),claim.eligibilityEvidenceId());
            ExternalAuditEvent event=new ExternalAuditEvent(eventId,"ROLE_TASK_CLAIMED","1",
                    claim.claimTime().toInstant(java.time.ZoneOffset.UTC),source.enterpriseId,
                    claim.instanceId().toString(),claim.nodeExecutionId().toString(),claim.taskId().toString(),
                    claim.id().toString(),claim.candidateUserId().toString(),source.runtimeBindingId,
                    claim.admissionId(),claim.eligibilityEvidenceId().toString(),source.directoryRevision,
                    source.directoryResultHash,source.candidatePoolHash,claim.eligibilitySnapshotHash(),
                    eventHash,claim.traceId(),null).withComputedHash();
            int inserted=jdbc.update("""
                    INSERT INTO workflow_role_external_audit_outbox
                    (id,audit_event_id,claim_id,claim_audit_id,provider_code,provider_version,payload_hash,
                     status,attempt_count,next_attempt_time,created_by,created_time,updated_by,updated_time,deleted,delete_token,version)
                    VALUES (?,?,?,?,?,?,?,'PENDING',0,?,?,?, ?,?,0,0,0)
                    """,id,eventId,claim.id(),claimAuditId,PROVIDER_CODE,PROVIDER_VERSION,event.payloadHash(),
                    Timestamp.from(now),claim.operatorUserId().toString(),Timestamp.from(now),
                    claim.operatorUserId().toString(),Timestamp.from(now));
            if(inserted!=1) throw new BusinessException("BROLEAUDIT","external audit outbox insert failed");
            int payload=jdbc.update("""
                    INSERT INTO workflow_role_external_audit_payload
                    (outbox_id,audit_event_id,event_payload,payload_hash,created_time)
                    VALUES (?,?,CAST(? AS JSON),?,?)
                    """,id,eventId,json.writeValueAsString(event),event.payloadHash(),Timestamp.from(now));
            if(payload!=1) throw new BusinessException("BROLEAUDIT","external audit payload insert failed");
        } catch (DataAccessException ex) {
            throw new BusinessException("BROLEAUDIT","external audit outbox unavailable");
        } catch (Exception ex) {
            throw new BusinessException("BROLEAUDIT","external audit payload unavailable");
        }
    }

    public boolean ready(){
        try { jdbc.queryForObject("SELECT COUNT(*) FROM workflow_role_external_audit_outbox WHERE 1=0",Long.class); return true; }
        catch(DataAccessException ex){return false;}
    }
    private record EventSource(String enterpriseId,String runtimeBindingId,String directoryRevision,
            String directoryResultHash,String candidatePoolHash){}
}
