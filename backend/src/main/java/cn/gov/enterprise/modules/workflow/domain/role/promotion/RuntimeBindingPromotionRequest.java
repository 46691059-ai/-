package cn.gov.enterprise.modules.workflow.domain.role.promotion;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationCanonical;
import java.time.Instant;
import java.util.Objects;

/** Immutable request to promote approved evidence into a non-executable candidate. */
public record RuntimeBindingPromotionRequest(
        String promotionId,
        String activationId,
        String activationHash,
        ResolverCode resolverCode,
        ResolverVersion resolverVersion,
        ResolverContractHash resolverContractHash,
        String bindingHash,
        String candidateHash,
        long directoryRevision,
        String businessScope,
        Instant effectiveFrom,
        Instant effectiveUntil,
        String requestedBy,
        boolean promotionPermissionGranted,
        String permissionEvidenceHash) {

    public RuntimeBindingPromotionRequest {
        promotionId = PersistentActivationCanonical.text(promotionId, "promotionId", 100);
        activationId = PersistentActivationCanonical.text(activationId, "activationId", 100);
        activationHash = PersistentActivationCanonical.hash(activationHash, "activationHash");
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(resolverContractHash, "resolverContractHash");
        bindingHash = PersistentActivationCanonical.hash(bindingHash, "bindingHash");
        candidateHash = PersistentActivationCanonical.hash(candidateHash, "candidateHash");
        if (directoryRevision < 0) throw new IllegalArgumentException("directoryRevision must be non-negative");
        businessScope = PersistentActivationCanonical.text(businessScope, "businessScope", 200);
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(effectiveUntil, "effectiveUntil");
        if (!effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
        }
        requestedBy = PersistentActivationCanonical.text(requestedBy, "requestedBy", 100);
        permissionEvidenceHash = PersistentActivationCanonical.hash(
                permissionEvidenceHash, "permissionEvidenceHash");
    }
}
