package cn.gov.enterprise.modules.governance.audit.domain;

import java.time.Instant;
import java.util.Objects;

public record ExternalAuditReceipt(String auditEventId,String externalReceiptId,
        String providerCode,String providerVersion,Instant receivedAt,String payloadHash,
        Status receiptStatus,String receiptHash) {
    public enum Status { ACCEPTED, REJECTED }
    public static final String CANONICAL_VERSION="EXTERNAL_AUDIT_RECEIPT_V1";
    public ExternalAuditReceipt {
        if(auditEventId==null||auditEventId.isBlank()||externalReceiptId==null||externalReceiptId.isBlank()
                ||providerCode==null||providerCode.isBlank()||providerVersion==null||providerVersion.isBlank())
            throw new IllegalArgumentException("receipt identity is required");
        Objects.requireNonNull(receivedAt); Objects.requireNonNull(receiptStatus);
        if(payloadHash==null||!payloadHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid receipt payloadHash");
        if(receiptHash!=null&&!receiptHash.equals(computeHash(auditEventId,externalReceiptId,providerCode,
                providerVersion,receivedAt,payloadHash,receiptStatus))) throw new IllegalArgumentException("receiptHash mismatch");
    }
    public ExternalAuditReceipt withComputedHash(){return new ExternalAuditReceipt(auditEventId,externalReceiptId,
            providerCode,providerVersion,receivedAt,payloadHash,receiptStatus,
            computeHash(auditEventId,externalReceiptId,providerCode,providerVersion,receivedAt,payloadHash,receiptStatus));}
    public boolean verify(){return withComputedHash().receiptHash.equals(receiptHash);}
    private static String computeHash(String eventId,String receiptId,String provider,String version,
            Instant received,String payload,Status status){
        return ExternalAuditEvent.sha256(String.join("\n",CANONICAL_VERSION,eventId,receiptId,provider,
                version,received.toString(),payload,status.name()));
    }
}
