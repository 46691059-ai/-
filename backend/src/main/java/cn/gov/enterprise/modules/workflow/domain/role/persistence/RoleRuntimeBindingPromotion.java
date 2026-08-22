package cn.gov.enterprise.modules.workflow.domain.role.persistence;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationCanonical;
import java.time.Instant;
import java.util.Objects;

/** Append-only persisted PROMOTED governance fact; not an executable binding. */
public record RoleRuntimeBindingPromotion(
        Long id, String promotionId, String activationId,
        String activationHash, String activationAuditHash, String approvalEvidenceHash,
        ResolverCode resolverCode, ResolverVersion resolverVersion,
        ResolverContractHash resolverContractHash, String bindingHash, String candidateHash,
        String directoryContractHash, long directoryRevision, String businessScope,
        Instant effectiveFrom, Instant effectiveUntil, String permissionEvidenceHash,
        String promotionEvidenceHash, String promotionHash, Instant promotedAt,
        String promotedBy, int version) {

    public RoleRuntimeBindingPromotion {
        positive(id, "id");
        promotionId = text(promotionId, "promotionId", 100);
        activationId = text(activationId, "activationId", 100);
        activationHash = hash(activationHash, "activationHash");
        activationAuditHash = hash(activationAuditHash, "activationAuditHash");
        approvalEvidenceHash = hash(approvalEvidenceHash, "approvalEvidenceHash");
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(resolverContractHash, "resolverContractHash");
        bindingHash = hash(bindingHash, "bindingHash");
        candidateHash = hash(candidateHash, "candidateHash");
        directoryContractHash = hash(directoryContractHash, "directoryContractHash");
        if (directoryRevision < 0) throw new IllegalArgumentException("directoryRevision must be non-negative");
        businessScope = text(businessScope, "businessScope", 200);
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(effectiveUntil, "effectiveUntil");
        if (!effectiveUntil.isAfter(effectiveFrom)) throw new IllegalArgumentException("invalid effective window");
        permissionEvidenceHash = hash(permissionEvidenceHash, "permissionEvidenceHash");
        promotionEvidenceHash = hash(promotionEvidenceHash, "promotionEvidenceHash");
        promotionHash = hash(promotionHash, "promotionHash");
        Objects.requireNonNull(promotedAt, "promotedAt");
        promotedBy = text(promotedBy, "promotedBy", 100);
        if (version != 0) throw new IllegalArgumentException("append-only promotion version must be zero");
    }

    static String hash(String value, String field) {
        return PersistentActivationCanonical.hash(value, field);
    }

    static String text(String value, String field, int max) {
        return PersistentActivationCanonical.text(value, field, max);
    }

    static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
