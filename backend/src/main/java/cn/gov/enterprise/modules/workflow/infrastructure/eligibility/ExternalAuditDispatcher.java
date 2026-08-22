package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.modules.governance.audit.domain.*;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit worker. CAS claim commits before HTTPS, so no database lock is held over the network. */
public final class ExternalAuditDispatcher {
    private final JdbcTemplate jdbc; private final WorkflowIdentityGenerator ids; private final ExternalAuditSinkClient sink; private final ObjectMapper json; private final ExternalAuditMetrics metrics;
    public ExternalAuditDispatcher(JdbcTemplate jdbc,WorkflowIdentityGenerator ids,ExternalAuditSinkClient sink,ObjectMapper json){this(jdbc,ids,sink,json,null);}
    public ExternalAuditDispatcher(JdbcTemplate jdbc,WorkflowIdentityGenerator ids,ExternalAuditSinkClient sink,ObjectMapper json,ExternalAuditMetrics metrics){this.jdbc=jdbc;this.ids=ids;this.sink=sink;this.json=json;this.metrics=metrics;}
    public int dispatch(int limit){
        List<Row> rows=jdbc.query("""
            SELECT o.id,o.audit_event_id,o.version,p.event_payload
            FROM workflow_role_external_audit_outbox o JOIN workflow_role_external_audit_payload p ON p.outbox_id=o.id
            WHERE o.deleted=0 AND o.status IN ('PENDING','RETRY')
              AND (o.next_attempt_time IS NULL OR o.next_attempt_time<=CURRENT_TIMESTAMP(3))
            ORDER BY o.id LIMIT ?
            """,(rs,n)->new Row(rs.getLong(1),rs.getString(2),rs.getInt(3),rs.getString(4)),limit);
        int delivered=0;
        for(Row row:rows){
            int claimed=jdbc.update("UPDATE workflow_role_external_audit_outbox SET status='SENDING',attempt_count=attempt_count+1,updated_time=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=? AND version=? AND status IN ('PENDING','RETRY')",row.id,row.version);
            if(claimed!=1)continue;
            long started=System.nanoTime();try{
                ExternalAuditEvent event=json.readValue(row.payloadJson,ExternalAuditEvent.class);
                ExternalAuditReceipt receipt=sink.deliver(event);
                jdbc.update("""
                    INSERT INTO workflow_role_external_audit_receipt
                    (id,outbox_id,audit_event_id,external_receipt_id,provider_code,provider_version,received_at,payload_hash,receipt_hash,status,
                     created_by,created_time,updated_by,updated_time,deleted,delete_token,version)
                    VALUES (?,?,?,?,?,?,?,?,?,?,'SYSTEM',CURRENT_TIMESTAMP(3),'SYSTEM',CURRENT_TIMESTAMP(3),0,0,0)
                    """,ids.nextId(),row.id,row.eventId,receipt.externalReceiptId(),receipt.providerCode(),receipt.providerVersion(),
                        Timestamp.from(receipt.receivedAt()),receipt.payloadHash(),receipt.receiptHash(),receipt.receiptStatus().name());
                jdbc.update("UPDATE workflow_role_external_audit_outbox SET status='ACKNOWLEDGED',last_error_code=NULL,next_attempt_time=NULL,updated_time=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=? AND status='SENDING'",row.id);
                delivered++;
                if(metrics!=null){metrics.delivery("SUCCESS",System.nanoTime()-started);metrics.receipt("ACCEPTED");}
            }catch(ExternalAuditSinkClient.DeliveryException failure){fail(row.id,failure.code(),failure.retryable());if(metrics!=null)metrics.delivery(failure.retryable()?"RETRY":"DEAD",System.nanoTime()-started);}
            catch(Exception malformed){fail(row.id,"LOCAL_PAYLOAD_INVALID",false);if(metrics!=null)metrics.delivery("DEAD",System.nanoTime()-started);}
        }
        return delivered;
    }
    public int replayDead(String auditEventId){int changed=jdbc.update("UPDATE workflow_role_external_audit_outbox SET status='RETRY',last_error_code='CONTROLLED_REPLAY',next_attempt_time=CURRENT_TIMESTAMP(3),updated_time=CURRENT_TIMESTAMP(3),version=version+1 WHERE audit_event_id=? AND status='DEAD'",auditEventId);if(changed==1&&metrics!=null)metrics.replay();return changed;}
    private void fail(long id,String code,boolean retryable){
        if(retryable)jdbc.update("UPDATE workflow_role_external_audit_outbox SET status=IF(attempt_count>=10,'DEAD','RETRY'),last_error_code=?,next_attempt_time=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL LEAST(300,POW(2,attempt_count)) SECOND),updated_time=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=? AND status='SENDING'",code,id);
        else jdbc.update("UPDATE workflow_role_external_audit_outbox SET status='DEAD',last_error_code=?,next_attempt_time=NULL,updated_time=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=? AND status='SENDING'",code,id);
    }
    private record Row(long id,String eventId,int version,String payloadJson){}
}
