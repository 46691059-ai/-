package cn.gov.enterprise.modules.workflow.domain.role.promotion;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationCanonical;
import java.time.Instant;
import java.util.Objects;

/** Non-executable governance output. It is not a WorkflowResolverBinding. */
public record RuntimeBindingCandidate(
        String promotionId,
        String promotionHash,
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
        String promotionEvidenceHash,
        Instant promotedAt,
        boolean runtimeEnabled) {

    public RuntimeBindingCandidate {
        promotionId = PersistentActivationCanonical.text(promotionId, "promotionId", 100);
        promotionHash = PersistentActivationCanonical.hash(promotionHash, "promotionHash");
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
        promotionEvidenceHash = PersistentActivationCanonical.hash(
                promotionEvidenceHash, "promotionEvidenceHash");
        Objects.requireNonNull(promotedAt, "promotedAt");
        if (runtimeEnabled) throw new IllegalArgumentException("promotion candidate must never enable ROLE Runtime");
    }
}
