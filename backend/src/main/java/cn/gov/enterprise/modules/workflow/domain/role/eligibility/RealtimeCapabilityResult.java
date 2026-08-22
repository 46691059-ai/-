package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.time.Instant;

/** Minimal, PII-free result returned by a capability port. */
public record RealtimeCapabilityResult(
        Outcome outcome,
        String reason,
        String evidenceHash,
        String policyVersion,
        Instant validUntil) {
    public enum Outcome { PASS, DENY, INDETERMINATE }

    public RealtimeCapabilityResult {
        if (outcome == null) throw new IllegalArgumentException("outcome is required");
        reason = RealtimeEligibilityQuery.text(reason, "reason", 300);
        evidenceHash = RealtimeEligibilityQuery.hash(evidenceHash, "evidenceHash");
        policyVersion = RealtimeEligibilityQuery.text(policyVersion, "policyVersion", 100);
    }

    public static RealtimeCapabilityResult pass(String reason, String hash, String version, Instant until) {
        return new RealtimeCapabilityResult(Outcome.PASS, reason, hash, version, until);
    }
}
