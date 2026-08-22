package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.util.Objects;

/** Governance artifact only; it is not a WorkflowResolverBinding or NodeResolverBinding. */
public record RuntimeBindingCandidate(
        String proposalHash, RoleRuntimeEligibility.Status eligibilityStatus,
        Instant generatedAt) {
    public RuntimeBindingCandidate {
        proposalHash = RoleCandidateResult.hash(proposalHash, "proposalHash");
        Objects.requireNonNull(eligibilityStatus, "eligibilityStatus");
        Objects.requireNonNull(generatedAt, "generatedAt");
    }

    public static RuntimeBindingCandidate from(
            RoleRuntimeEligibility eligibility, Instant generatedAt) {
        if (!eligibility.ready()) {
            throw new IllegalStateException("only READY eligibility can produce a runtime candidate");
        }
        return new RuntimeBindingCandidate(eligibility.bindingHash(),
                eligibility.status(), generatedAt);
    }
}
