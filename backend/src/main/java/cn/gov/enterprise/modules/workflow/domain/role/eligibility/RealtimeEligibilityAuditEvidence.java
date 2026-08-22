package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.time.Instant;
import java.util.Objects;

/** Append-only shaped audit value; contains stable identifiers and hashes, never personal profile data. */
public record RealtimeEligibilityAuditEvidence(
        long taskId,
        long candidateUserId,
        RealtimeEligibilityStatus outcome,
        String reasonCode,
        String candidateRevision,
        String claimRevision,
        String eligibilityHash,
        Instant verifiedAt,
        Instant expiresAt,
        String correlationId) {
    public RealtimeEligibilityAuditEvidence {
        if (taskId <= 0 || candidateUserId <= 0) throw new IllegalArgumentException("invalid audit identity");
        Objects.requireNonNull(outcome, "outcome");
        reasonCode = RealtimeEligibilityQuery.upperCode(reasonCode, "reasonCode");
        candidateRevision = RealtimeEligibilityQuery.text(candidateRevision, "candidateRevision", 100);
        if (claimRevision != null) claimRevision = RealtimeEligibilityQuery.text(claimRevision, "claimRevision", 100);
        eligibilityHash = RealtimeEligibilityQuery.hash(eligibilityHash, "eligibilityHash");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        correlationId = RealtimeEligibilityQuery.text(correlationId, "correlationId", 100);
    }
}
