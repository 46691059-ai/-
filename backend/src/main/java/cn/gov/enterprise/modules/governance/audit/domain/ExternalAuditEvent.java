package cn.gov.enterprise.modules.governance.audit.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** PII-minimal, versioned contract shared by the Workflow adapter and Audit Sink. */
public record ExternalAuditEvent(
        String auditEventId, String eventType, String eventVersion, Instant occurredAt,
        String enterpriseId, String workflowInstanceId, String nodeExecutionId,
        String taskId, String claimId, String candidateUserId, String runtimeBindingId,
        String admissionId, String eligibilityEvidenceId, String directoryRevision,
        String directoryResultHash, String candidatePoolHash, String eligibilityHash,
        String claimAuditHash, String correlationId, String payloadHash) {

    public static final String CONTRACT_VERSION = "ROLE_CLAIM_AUDIT_EVENT_V1";

    public ExternalAuditEvent {
        auditEventId = required(auditEventId, "auditEventId");
        eventType = required(eventType, "eventType");
        eventVersion = required(eventVersion, "eventVersion");
        Objects.requireNonNull(occurredAt, "occurredAt");
        enterpriseId = required(enterpriseId, "enterpriseId");
        workflowInstanceId = required(workflowInstanceId, "workflowInstanceId");
        nodeExecutionId = required(nodeExecutionId, "nodeExecutionId");
        taskId = required(taskId, "taskId"); claimId = required(claimId, "claimId");
        candidateUserId = required(candidateUserId, "candidateUserId");
        runtimeBindingId = required(runtimeBindingId, "runtimeBindingId");
        admissionId = required(admissionId, "admissionId");
        eligibilityEvidenceId = required(eligibilityEvidenceId, "eligibilityEvidenceId");
        directoryRevision = required(directoryRevision, "directoryRevision");
        directoryResultHash = hash(directoryResultHash, "directoryResultHash");
        candidatePoolHash = hash(candidatePoolHash, "candidatePoolHash");
        eligibilityHash = hash(eligibilityHash, "eligibilityHash");
        claimAuditHash = hash(claimAuditHash, "claimAuditHash");
        correlationId = required(correlationId, "correlationId");
        if (payloadHash != null && !payloadHash.equals(computePayloadHash(
                auditEventId,eventType,eventVersion,occurredAt,enterpriseId,workflowInstanceId,
                nodeExecutionId,taskId,claimId,candidateUserId,runtimeBindingId,admissionId,
                eligibilityEvidenceId,directoryRevision,directoryResultHash,candidatePoolHash,
                eligibilityHash,claimAuditHash,correlationId))) {
            throw new IllegalArgumentException("payloadHash mismatch");
        }
    }

    public ExternalAuditEvent withComputedHash() {
        return new ExternalAuditEvent(auditEventId,eventType,eventVersion,occurredAt,enterpriseId,
                workflowInstanceId,nodeExecutionId,taskId,claimId,candidateUserId,runtimeBindingId,
                admissionId,eligibilityEvidenceId,directoryRevision,directoryResultHash,
                candidatePoolHash,eligibilityHash,claimAuditHash,correlationId,
                computePayloadHash(auditEventId,eventType,eventVersion,occurredAt,enterpriseId,
                        workflowInstanceId,nodeExecutionId,taskId,claimId,candidateUserId,
                        runtimeBindingId,admissionId,eligibilityEvidenceId,directoryRevision,
                        directoryResultHash,candidatePoolHash,eligibilityHash,claimAuditHash,
                        correlationId));
    }

    public boolean verifyPayloadHash() { return withComputedHash().payloadHash.equals(payloadHash); }

    private static String computePayloadHash(String... values) {
        return sha256(CONTRACT_VERSION + "\n" + String.join("\n", values));
    }
    private static String computePayloadHash(String eventId,String type,String version,Instant occurredAt,
            String... rest) {
        String[] values=new String[rest.length+4]; values[0]=CONTRACT_VERSION; values[1]=eventId;
        values[2]=type; values[3]=version; System.arraycopy(rest,0,values,4,rest.length);
        return sha256(String.join("\n",values)+"\n"+occurredAt.toString());
    }
    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String required(String value,String field){
        if(value==null||value.isBlank()) throw new IllegalArgumentException(field+" is required");
        return value.trim();
    }
    private static String hash(String value,String field){
        value=required(value,field); if(!value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException(field+" must be lowercase SHA-256"); return value;
    }
}
