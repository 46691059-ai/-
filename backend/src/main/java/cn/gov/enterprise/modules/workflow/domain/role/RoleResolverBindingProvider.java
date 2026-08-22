package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import java.util.List;
import java.util.Objects;

/** Calculates ROLE binding proposals without creating runtime bindings or tasks. */
public final class RoleResolverBindingProvider {
    private final ResolverRegistry resolverRegistry;

    public RoleResolverBindingProvider(ResolverRegistry resolverRegistry) {
        this.resolverRegistry = Objects.requireNonNull(resolverRegistry, "resolverRegistry");
    }

    public ResolverBindingProposalSet propose(
            Long definitionId, Long versionId, List<RoleResolverBindingRequest> requests) {
        AssignmentResolverDescriptor descriptor = resolverRegistry.requireDescriptor(
                RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                ResolverStatus.PREPARED, RoleDirectoryResolver.CONTRACT_HASH);
        List<RoleResolverBindingProposal> proposals = List.copyOf(requests).stream()
                .map(request -> new RoleResolverBindingProposal(request.nodeId(), descriptor.code(),
                        descriptor.version(), descriptor.contractHash(), request.roleCode(),
                        request.organizationId(), request.effectiveAt(),
                        CandidateResolutionMode.ROLE_POOL_PREVIEW))
                .toList();
        ResolverBindingProposalSet set = ResolverBindingProposalSet.freeze(
                definitionId, versionId, proposals);
        RoleBindingValidationResult validation = validate(set);
        if (!validation.valid()) {
            throw new IllegalArgumentException("invalid ROLE binding proposal: " + validation.status());
        }
        return set;
    }

    public RoleBindingValidationResult validate(ResolverBindingProposalSet set) {
        if (set == null || !set.hasValidHash()) {
            return RoleBindingValidationResult.invalid(
                    RoleBindingValidationResult.Status.INVALID_HASH, "binding hash mismatch");
        }
        for (RoleResolverBindingProposal proposal : set.proposals()) {
            if (proposal.roleCode().isBlank()) {
                return RoleBindingValidationResult.invalid(
                        RoleBindingValidationResult.Status.INVALID_ROLE, "roleCode is blank");
            }
            if (proposal.organizationId().isBlank()) {
                return RoleBindingValidationResult.invalid(
                        RoleBindingValidationResult.Status.INVALID_ORGANIZATION,
                        "organizationId is blank");
            }
            if (!proposal.resolverCode().equals(RoleDirectoryResolver.CODE)
                    || !proposal.resolverVersion().equals(RoleDirectoryResolver.VERSION)
                    || !proposal.contractHash().equals(RoleDirectoryResolver.CONTRACT_HASH)) {
                return RoleBindingValidationResult.invalid(
                        RoleBindingValidationResult.Status.INVALID_CONTRACT,
                        "ROLE resolver contract mismatch");
            }
            try {
                resolverRegistry.requireDescriptor(proposal.resolverCode(),
                        proposal.resolverVersion(), ResolverStatus.PREPARED,
                        proposal.contractHash());
            } catch (RuntimeException exception) {
                return RoleBindingValidationResult.invalid(
                        RoleBindingValidationResult.Status.INVALID_CONTRACT,
                        "ROLE resolver descriptor is not PREPARED");
            }
        }
        return RoleBindingValidationResult.validResult();
    }
}
