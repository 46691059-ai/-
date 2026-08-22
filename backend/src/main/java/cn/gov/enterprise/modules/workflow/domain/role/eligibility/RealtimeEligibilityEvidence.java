package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** PII-minimized immutable evidence. Null capability fields mean fail-fast stopped before that check. */
public record RealtimeEligibilityEvidence(
        long candidateUserId,
        String candidatePoolHash,
        String runtimeBindingHash,
        String roleCode,
        String organizationId,
        String candidateDirectoryRevision,
        String claimDirectoryRevision,
        String directoryResultHash,
        String directoryContractHash,
        RealtimeUserStatusResult userStatusResult,
        RealtimeCapabilityResult organizationMembershipResult,
        RealtimeRoleMembershipResult roleMembershipResult,
        RealtimeCapabilityResult dataScopeResult,
        RealtimeCapabilityResult platformSoDResult,
        RealtimeCapabilityResult businessSoDResult,
        RealtimeCapabilityResult featureFlagResult,
        RealtimeCapabilityResult canaryResult,
        RealtimeCapabilityResult killSwitchResult,
        RealtimeCapabilityResult auditCapabilityResult,
        List<RealtimeEligibilityValidationResult> validatorResults,
        Instant verifiedAt,
        Instant expiresAt,
        String businessScopeReference,
        String correlationId,
        String canonicalVersion,
        String eligibilityHash) {
    public RealtimeEligibilityEvidence {
        if (candidateUserId <= 0) throw new IllegalArgumentException("candidateUserId must be positive");
        candidatePoolHash = RealtimeEligibilityQuery.hash(candidatePoolHash, "candidatePoolHash");
        runtimeBindingHash = RealtimeEligibilityQuery.hash(runtimeBindingHash, "runtimeBindingHash");
        roleCode = RealtimeEligibilityQuery.upperCode(roleCode, "roleCode");
        organizationId = RealtimeEligibilityQuery.text(organizationId, "organizationId", 100);
        candidateDirectoryRevision = RealtimeEligibilityQuery.text(candidateDirectoryRevision, "candidateDirectoryRevision", 100);
        if (claimDirectoryRevision != null) claimDirectoryRevision = RealtimeEligibilityQuery.text(claimDirectoryRevision, "claimDirectoryRevision", 100);
        if (directoryResultHash != null) directoryResultHash = RealtimeEligibilityQuery.hash(directoryResultHash, "directoryResultHash");
        if (directoryContractHash != null) directoryContractHash = RealtimeEligibilityQuery.hash(directoryContractHash, "directoryContractHash");
        validatorResults = List.copyOf(Objects.requireNonNull(validatorResults, "validatorResults"));
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(verifiedAt)) throw new IllegalArgumentException("expiresAt must be after verifiedAt");
        businessScopeReference = RealtimeEligibilityQuery.text(businessScopeReference, "businessScopeReference", 200);
        correlationId = RealtimeEligibilityQuery.text(correlationId, "correlationId", 100);
        if (!RealtimeEligibilityPolicy.CANONICAL_VERSION.equals(canonicalVersion)) {
            throw new IllegalArgumentException("unsupported canonicalVersion");
        }
        eligibilityHash = RealtimeEligibilityQuery.hash(eligibilityHash, "eligibilityHash");
    }
}
