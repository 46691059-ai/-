package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.Objects;

/** Complete in-memory evidence required for eligibility evaluation. */
public record RoleRuntimeEligibilityRequest(
        ResolverBindingProposalSet proposalSet,
        RoleResolverBindingProposal proposal,
        RoleCandidateResult candidateResult,
        int candidateLimit) {
    public RoleRuntimeEligibilityRequest {
        Objects.requireNonNull(proposalSet, "proposalSet");
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(candidateResult, "candidateResult");
        if (candidateLimit <= 0) throw new IllegalArgumentException("candidateLimit must be positive");
    }
}
