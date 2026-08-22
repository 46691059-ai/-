package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.time.Instant;
import java.util.Objects;

/** In-memory evidence reference. It is not authorization and cannot commit a Claim. */
public record RealtimeEligibilityVerificationToken(
        long taskId,
        long candidateUserId,
        long taskVersion,
        String candidatePoolHash,
        String runtimeBindingHash,
        RealtimeEligibilityRevisionFence revisionFence,
        String eligibilityHash,
        Instant verifiedAt,
        Instant expiresAt) {
    public RealtimeEligibilityVerificationToken {
        if (taskId <= 0 || candidateUserId <= 0 || taskVersion < 0) throw new IllegalArgumentException("invalid token identity/version");
        candidatePoolHash = RealtimeEligibilityQuery.hash(candidatePoolHash, "candidatePoolHash");
        runtimeBindingHash = RealtimeEligibilityQuery.hash(runtimeBindingHash, "runtimeBindingHash");
        Objects.requireNonNull(revisionFence, "revisionFence");
        eligibilityHash = RealtimeEligibilityQuery.hash(eligibilityHash, "eligibilityHash");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
