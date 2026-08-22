package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Version-level collection of ROLE proposals with a deterministic binding hash. */
public record ResolverBindingProposalSet(
        Long definitionId, Long versionId,
        List<RoleResolverBindingProposal> proposals, String bindingHash) {
    public ResolverBindingProposalSet {
        positive(definitionId, "definitionId");
        positive(versionId, "versionId");
        proposals = List.copyOf(proposals).stream()
                .sorted(Comparator.comparing(RoleResolverBindingProposal::nodeId)).toList();
        if (proposals.isEmpty()) throw new IllegalArgumentException("proposals must not be empty");
        var nodes = new HashSet<Long>();
        if (proposals.stream().anyMatch(proposal -> !nodes.add(proposal.nodeId()))) {
            throw new IllegalArgumentException("a node may have only one ROLE proposal");
        }
        bindingHash = RoleCandidateResult.hash(bindingHash, "bindingHash");
    }

    public static ResolverBindingProposalSet freeze(
            Long definitionId, Long versionId, List<RoleResolverBindingProposal> proposals) {
        ResolverBindingProposalSet draft = new ResolverBindingProposalSet(
                definitionId, versionId, proposals, "0".repeat(64));
        return new ResolverBindingProposalSet(definitionId, versionId, proposals,
                RoleBindingCanonical.hash(draft));
    }

    public boolean hasValidHash() { return bindingHash.equals(RoleBindingCanonical.hash(this)); }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
