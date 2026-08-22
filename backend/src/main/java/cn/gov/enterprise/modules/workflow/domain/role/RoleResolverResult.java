package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.List;

/** Validated ROLE resolver output; persistence into CandidatePool is outside WF5.1. */
public record RoleResolverResult(
        List<String> candidateUsers, long directoryRevision,
        String resultHash, String resolverContractHash) {
    public RoleResolverResult {
        candidateUsers = List.copyOf(candidateUsers);
        if (candidateUsers.isEmpty()) throw new IllegalArgumentException("candidateUsers must not be empty");
        if (candidateUsers.stream().distinct().count() != candidateUsers.size()) {
            throw new IllegalArgumentException("candidateUsers must be unique");
        }
        if (directoryRevision <= 0) throw new IllegalArgumentException("directoryRevision must be positive");
        resultHash = RoleDirectoryQuery.required(resultHash, "resultHash", 64);
        resolverContractHash = RoleDirectoryQuery.required(
                resolverContractHash, "resolverContractHash", 64);
    }
}
