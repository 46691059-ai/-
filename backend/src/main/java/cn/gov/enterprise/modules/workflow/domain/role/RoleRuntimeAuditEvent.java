package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.util.Objects;

/** In-memory audit fact. Persistence is explicitly outside WF5.4. */
public record RoleRuntimeAuditEvent(
        String proposalHash, RoleRuntimeEligibility.Status validationResult,
        String failedReason, Instant occurredAt) {
    public RoleRuntimeAuditEvent {
        proposalHash = RoleCandidateResult.hash(proposalHash, "proposalHash");
        Objects.requireNonNull(validationResult, "validationResult");
        failedReason = failedReason == null ? "" : failedReason.trim();
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static RoleRuntimeAuditEvent capture(
            RoleRuntimeEligibility eligibility, Instant occurredAt) {
        return new RoleRuntimeAuditEvent(eligibility.bindingHash(), eligibility.status(),
                eligibility.ready() ? "" : eligibility.reason(), occurredAt);
    }
}
