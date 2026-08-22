package cn.gov.enterprise.modules.workflow.domain.role.promotion;

import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationCanonical;

/** ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1 deterministic hashing. */
public final class RuntimeBindingPromotionCanonical {
    public static final String VERSION = "ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1";

    private RuntimeBindingPromotionCanonical() { }

    public static String evidenceHash(RuntimeBindingPromotionEvidence evidence) {
        return evidenceHash(evidence.activationId(), evidence.activationStatus().name(), evidence.activationHash(),
                evidence.approvalEvidenceHash(), evidence.auditHash(), evidence.resolverCode(),
                evidence.resolverVersion(), evidence.resolverContractHash(), evidence.bindingHash(),
                evidence.candidateHash(), evidence.directoryContractHash(),
                evidence.directoryRevision(), evidence.businessScope(), evidence.effectiveAt().toString());
    }

    public static String evidenceHash(String activationId, String activationStatus, String activationHash,
            String approvalEvidenceHash, String auditHash, String resolverCode,
            String resolverVersion, String resolverContractHash, String bindingHash,
            String candidateHash, String directoryContractHash, long directoryRevision,
            String businessScope, String effectiveAt) {
        return hash("evidence", activationId, activationStatus, activationHash,
                approvalEvidenceHash, auditHash,
                resolverCode, resolverVersion, resolverContractHash, bindingHash, candidateHash,
                directoryContractHash, Long.toString(directoryRevision), businessScope, effectiveAt);
    }

    public static String promotionHash(RuntimeBindingPromotionRequest request,
            RuntimeBindingPromotionEvidence evidence) {
        return hash("promotion", request.promotionId(), request.activationId(),
                request.activationHash(), request.resolverCode().value(),
                request.resolverVersion().value(), request.resolverContractHash().value(),
                request.bindingHash(), request.candidateHash(),
                Long.toString(request.directoryRevision()), request.businessScope(),
                request.effectiveFrom().toString(), request.effectiveUntil().toString(),
                request.permissionEvidenceHash(), evidence.promotionEvidenceHash());
    }

    public static String auditEntryHash(String promotionId,
            RuntimeBindingPromotionStatus status, String reason, String timestamp) {
        return hash("audit", promotionId, status.name(), reason, timestamp);
    }

    private static String hash(String type, String... values) {
        StringBuilder canonical = new StringBuilder(VERSION).append('|').append(type);
        for (String value : values) {
            if (value == null) throw new IllegalArgumentException("canonical value must not be null");
            canonical.append('|').append(value.length()).append(':').append(value);
        }
        return PersistentActivationCanonical.sha256(canonical.toString());
    }
}
