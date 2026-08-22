package cn.gov.enterprise.modules.workflow.domain.role.promotion;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationStatus;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeEligibility;
import cn.gov.enterprise.modules.workflow.domain.role.RuntimeEligibilityRegistry;
import java.time.Instant;
import java.util.Objects;

/** Fixed-order, fail-closed Activation Evidence to Candidate policy. */
public final class RuntimeBindingPromotionPolicy {
    private final RuntimeEligibilityRegistry registry;

    public RuntimeBindingPromotionPolicy(RuntimeEligibilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public RuntimeBindingPromotionDecision promote(RuntimeBindingPromotionRequest request,
            RuntimeBindingPromotionEvidence evidence, Instant evaluatedAt) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        RuntimeBindingPromotionDecision decision = RuntimeBindingPromotionDecision.created(request, evaluatedAt)
                .advance(RuntimeBindingPromotionStatus.VALIDATING, "VALIDATION_STARTED", evaluatedAt);

        if (evidence == null) return blocked(decision, "ACTIVATION_EVIDENCE_MISSING", evaluatedAt);
        if (evidence.activationStatus() != PersistentActivationStatus.PERSISTED
                || !request.activationId().equals(evidence.activationId())) {
            return blocked(decision, "ACTIVATION_NOT_APPROVED", evaluatedAt);
        }
        if (!request.activationHash().equals(evidence.activationHash())) {
            return blocked(decision, "ACTIVATION_HASH_MISMATCH", evaluatedAt);
        }
        if (!request.resolverCode().value().equals(evidence.resolverCode())
                || !request.resolverVersion().value().equals(evidence.resolverVersion())
                || !request.resolverContractHash().value().equals(evidence.resolverContractHash())) {
            return blocked(decision, "RESOLVER_CONTRACT_HASH_MISMATCH", evaluatedAt);
        }
        if (!request.bindingHash().equals(evidence.bindingHash())) {
            return blocked(decision, "BINDING_HASH_MISMATCH", evaluatedAt);
        }
        if (!request.candidateHash().equals(evidence.candidateHash())) {
            return blocked(decision, "CANDIDATE_HASH_MISMATCH", evaluatedAt);
        }
        if (request.directoryRevision() != evidence.directoryRevision()) {
            return blocked(decision, "DIRECTORY_REVISION_MISMATCH", evaluatedAt);
        }
        if (!request.businessScope().equals(evidence.businessScope())
                || !request.effectiveFrom().equals(evidence.effectiveAt())
                || evaluatedAt.isBefore(request.effectiveFrom())
                || !evaluatedAt.isBefore(request.effectiveUntil())) {
            return blocked(decision, "EFFECTIVE_TIME_INVALID", evaluatedAt);
        }
        var registryResult = registry.inspect(request.resolverCode(), request.resolverVersion(),
                ResolverContractHash.of(evidence.resolverContractHash()));
        if (registryResult.status() != RoleRuntimeEligibility.Status.READY) {
            return blocked(decision, "RESOLVER_REGISTRY_REJECTED:" + registryResult.reason(), evaluatedAt);
        }
        if (!request.promotionPermissionGranted()) {
            return blocked(decision, "PROMOTION_PERMISSION_DENIED", evaluatedAt);
        }

        decision = decision.advance(RuntimeBindingPromotionStatus.ELIGIBLE,
                        "ALL_PROMOTION_GATES_PASSED", evaluatedAt)
                .advance(RuntimeBindingPromotionStatus.APPROVED,
                        "PROMOTION_PERMISSION_APPROVED", evaluatedAt);
        String promotionHash = RuntimeBindingPromotionCanonical.promotionHash(request, evidence);
        RuntimeBindingCandidate candidate = new RuntimeBindingCandidate(
                request.promotionId(), promotionHash, request.activationId(), request.activationHash(),
                request.resolverCode(), request.resolverVersion(), request.resolverContractHash(),
                request.bindingHash(), request.candidateHash(), request.directoryRevision(),
                request.businessScope(), request.effectiveFrom(), request.effectiveUntil(),
                evidence.promotionEvidenceHash(), evaluatedAt, false);
        return decision.promote(candidate, evaluatedAt);
    }

    private RuntimeBindingPromotionDecision blocked(RuntimeBindingPromotionDecision decision,
            String reason, Instant at) {
        return decision.advance(RuntimeBindingPromotionStatus.BLOCKED, reason, at);
    }
}
