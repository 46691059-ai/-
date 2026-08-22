package cn.gov.enterprise.modules.organization.approvalrole.domain.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

/** Immutable, PII-minimized proof of one physical provider invocation. */
public record ApprovalRoleDirectoryProviderAuditEvidence(
        String auditId, String auditEventId, String correlationId, String requestId,
        String providerCode, String providerVersion, String serviceIdentity,
        String environmentIdentity, String callerServiceIdentity,
        String enterpriseId, String organizationId, String roleCode, Instant effectiveAt,
        String contractVersion, String contractHash, String canonicalVersion,
        Long directoryRevision, String directoryResultHash, Integer candidateCount,
        Outcome outcome, String failureCode, String requestHash, String evidenceHash,
        Instant startedAt, Instant completedAt) {

    public static final String REQUEST_CANONICAL = "APPROVAL_ROLE_DIRECTORY_PROVIDER_REQUEST_V1";
    public static final String EVIDENCE_CANONICAL = "APPROVAL_ROLE_DIRECTORY_PROVIDER_AUDIT_V1";
    public enum Outcome { SUCCESS, REJECTED, FAILED }

    public ApprovalRoleDirectoryProviderAuditEvidence {
        required(auditId,"auditId"); required(auditEventId,"auditEventId"); required(requestId,"requestId");
        required(providerCode,"providerCode"); required(providerVersion,"providerVersion");
        required(serviceIdentity,"serviceIdentity"); required(environmentIdentity,"environmentIdentity");
        required(contractVersion,"contractVersion"); hash(contractHash,"contractHash");
        required(canonicalVersion,"canonicalVersion"); hash(requestHash,"requestHash"); hash(evidenceHash,"evidenceHash");
        if(outcome==null) throw new IllegalArgumentException("outcome is required");
        if(startedAt==null||completedAt==null||completedAt.isBefore(startedAt)) throw new IllegalArgumentException("audit timestamps are invalid");
        if(outcome==Outcome.SUCCESS){
            if(directoryRevision==null||directoryRevision<0||candidateCount==null||candidateCount<0) throw new IllegalArgumentException("successful result facts are required");
            hash(directoryResultHash,"directoryResultHash"); required(enterpriseId,"enterpriseId");required(organizationId,"organizationId");required(roleCode,"roleCode");
            if(effectiveAt==null)throw new IllegalArgumentException("effectiveAt is required");
            if(failureCode!=null)throw new IllegalArgumentException("successful evidence cannot carry failureCode");
        } else required(failureCode,"failureCode");
        if(!computedEvidenceHash(auditEventId,requestHash,providerCode,providerVersion,serviceIdentity,environmentIdentity,
                contractHash,directoryRevision,directoryResultHash,candidateCount,outcome,failureCode,startedAt,completedAt).equals(evidenceHash))
            throw new IllegalArgumentException("evidenceHash mismatch");
    }

    public static ApprovalRoleDirectoryProviderAuditEvidence create(String correlationId,String requestId,
            String providerCode,String providerVersion,String serviceIdentity,String environmentIdentity,
            String callerServiceIdentity,String enterpriseId,String organizationId,String roleCode,Instant effectiveAt,
            String contractVersion,String contractHash,String canonicalVersion,Long revision,String resultHash,
            Integer candidateCount,Outcome outcome,String failureCode,Instant startedAt,Instant completedAt){
        Instant start=startedAt.truncatedTo(ChronoUnit.MILLIS), end=completedAt.truncatedTo(ChronoUnit.MILLIS);
        String auditId=UUID.randomUUID().toString(),auditEventId=UUID.randomUUID().toString();
        String req=requestHash(callerServiceIdentity,enterpriseId,organizationId,roleCode,effectiveAt,contractVersion,correlationId);
        String evidence=computedEvidenceHash(auditEventId,req,providerCode,providerVersion,serviceIdentity,environmentIdentity,
                contractHash,revision,resultHash,candidateCount,outcome,failureCode,start,end);
        return new ApprovalRoleDirectoryProviderAuditEvidence(auditId,auditEventId,
                clean(correlationId),requiredValue(requestId,"requestId"),providerCode,providerVersion,serviceIdentity,
                environmentIdentity,clean(callerServiceIdentity),clean(enterpriseId),clean(organizationId),clean(roleCode),effectiveAt,
                contractVersion,contractHash,canonicalVersion,revision,resultHash,candidateCount,outcome,clean(failureCode),req,evidence,start,end);
    }

    public static String requestHash(String caller,String enterprise,String organization,String role,Instant effectiveAt,
            String contractVersion,String correlationId){
        // correlationId is included: it identifies a physical trusted query; requestId remains queryable but non-idempotent.
        return sha256(canonical(REQUEST_CANONICAL,caller,enterprise,organization,role,
                effectiveAt==null?null:effectiveAt.toString(),contractVersion,correlationId));
    }
    public static String computedEvidenceHash(String auditEventId,String requestHash,String providerCode,String providerVersion,
            String serviceIdentity,String environment,String contractHash,Long revision,String resultHash,Integer count,
            Outcome outcome,String failureCode,Instant startedAt,Instant completedAt){
        return sha256(canonical(EVIDENCE_CANONICAL,auditEventId,requestHash,providerCode,providerVersion,serviceIdentity,environment,
                contractHash,value(revision),resultHash,value(count),outcome==null?null:outcome.name(),failureCode,
                startedAt==null?null:startedAt.toString(),completedAt==null?null:completedAt.toString()));
    }
    private static String canonical(String... values){StringBuilder out=new StringBuilder();for(String value:values){String v=value==null?"":value;out.append(v.length()).append(':').append(v).append('\n');}return out.toString();}
    private static String value(Object value){return value==null?null:value.toString();}
    private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String requiredValue(String value,String field){required(value,field);return value.trim();}
    private static void required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");}
    private static void hash(String value,String field){if(value==null||!value.matches("[0-9a-f]{64}"))throw new IllegalArgumentException(field+" must be lowercase SHA-256");}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
