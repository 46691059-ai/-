package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.util.Objects;

public record RealtimeEligibilityResult(
        RealtimeEligibilityDecision decision,
        RealtimeEligibilityEvidence evidence,
        RealtimeEligibilityAuditEvidence auditEvidence) {
    public RealtimeEligibilityResult {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(auditEvidence, "auditEvidence");
        if (decision.status() != auditEvidence.outcome()) throw new IllegalArgumentException("outcome mismatch");
    }
}
