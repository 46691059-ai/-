package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.Objects;

/** Evaluates promotion readiness without enabling or executing ROLE runtime. */
public final class RoleRuntimeEligibilityValidator {
    private final RuntimeEligibilityRegistry registry;

    public RoleRuntimeEligibilityValidator(RuntimeEligibilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public RoleRuntimeEligibility validate(RoleRuntimeEligibilityRequest request) {
        Objects.requireNonNull(request, "request");
        RoleResolverBindingProposal proposal = request.proposal();
        ResolverEligibilityResult resolver = registry.inspect(proposal.resolverCode(),
                proposal.resolverVersion(), proposal.contractHash());
        if (!resolver.ready()) return result(proposal, request.proposalSet(),
                RoleRuntimeEligibility.Status.BLOCKED, resolver.reason());
        if (!request.proposalSet().hasValidHash()) return result(proposal,
                request.proposalSet(), RoleRuntimeEligibility.Status.BLOCKED,
                "BINDING_HASH_MISMATCH");
        if (request.proposalSet().proposals().stream().noneMatch(proposal::equals)) {
            return result(proposal, request.proposalSet(),
                    RoleRuntimeEligibility.Status.REJECTED, "PROPOSAL_NOT_IN_BINDING_SET");
        }
        if (proposal.roleCode().isBlank()) return result(proposal, request.proposalSet(),
                RoleRuntimeEligibility.Status.REJECTED, "ROLE_RULE_INCOMPLETE");
        if (proposal.organizationId().isBlank()) return result(proposal,
                request.proposalSet(), RoleRuntimeEligibility.Status.REJECTED,
                "ORGANIZATION_INVALID");
        RoleCandidateResult candidates = request.candidateResult();
        if (!candidates.roleCode().equals(proposal.roleCode())
                || !candidates.organizationId().equals(proposal.organizationId())
                || !candidates.resolverContractHash().equals(proposal.contractHash().value())) {
            return result(proposal, request.proposalSet(),
                    RoleRuntimeEligibility.Status.BLOCKED, "CANDIDATE_CONTRACT_MISMATCH");
        }
        if (!candidates.hasValidCandidateHash()) return result(proposal,
                request.proposalSet(), RoleRuntimeEligibility.Status.BLOCKED,
                "CANDIDATE_CANONICAL_HASH_MISMATCH");
        if (candidates.candidateUsers().isEmpty()) return result(proposal,
                request.proposalSet(), RoleRuntimeEligibility.Status.BLOCKED,
                "EMPTY_CANDIDATE_SET");
        if (candidates.candidateUsers().size() > request.candidateLimit()) {
            return result(proposal, request.proposalSet(),
                    RoleRuntimeEligibility.Status.BLOCKED, "CANDIDATE_LIMIT_EXCEEDED");
        }
        return result(proposal, request.proposalSet(),
                RoleRuntimeEligibility.Status.READY, "ELIGIBILITY_CHECK_PASSED");
    }

    public String runtimeHash(
            RoleRuntimeEligibility eligibility, RoleCandidateResult candidates,
            int candidateLimit) {
        Objects.requireNonNull(eligibility, "eligibility");
        Objects.requireNonNull(candidates, "candidates");
        if (candidateLimit <= 0) throw new IllegalArgumentException("candidateLimit must be positive");
        return RoleRuntimeCanonical.hash(eligibility, candidates.candidateHash(),
                candidates.candidateUsers().size(), candidateLimit);
    }

    private RoleRuntimeEligibility result(RoleResolverBindingProposal proposal,
            ResolverBindingProposalSet set, RoleRuntimeEligibility.Status status,
            String reason) {
        return new RoleRuntimeEligibility(proposal.resolverCode(), proposal.resolverVersion(),
                proposal.contractHash(), set.bindingHash(), proposal.roleCode(),
                proposal.organizationId(), status, reason);
    }
}
