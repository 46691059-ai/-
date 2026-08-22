package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.time.Instant;

/** Authority result for one frozen candidate only; never a candidate enumeration. */
public record RealtimeRoleMembershipResult(
        RealtimeCapabilityResult.Outcome outcome,
        boolean complete,
        boolean sourceConflict,
        String claimRevision,
        String directoryResultHash,
        String contractHash,
        String reason,
        String evidenceHash,
        Instant validUntil) {
    public RealtimeRoleMembershipResult {
        if (outcome == null) throw new IllegalArgumentException("outcome is required");
        claimRevision = RealtimeEligibilityQuery.text(claimRevision, "claimRevision", 100);
        directoryResultHash = RealtimeEligibilityQuery.hash(directoryResultHash, "directoryResultHash");
        contractHash = RealtimeEligibilityQuery.hash(contractHash, "contractHash");
        reason = RealtimeEligibilityQuery.text(reason, "reason", 300);
        evidenceHash = RealtimeEligibilityQuery.hash(evidenceHash, "evidenceHash");
    }
}
