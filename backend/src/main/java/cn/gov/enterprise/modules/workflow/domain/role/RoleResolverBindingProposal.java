package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.time.Instant;
import java.util.Objects;

/** Metadata-only node binding proposal; it cannot be persisted as a runtime binding. */
public record RoleResolverBindingProposal(
        Long nodeId, ResolverCode resolverCode, ResolverVersion resolverVersion,
        ResolverContractHash contractHash, String roleCode, String organizationId,
        Instant effectiveAt, CandidateResolutionMode candidateMode) {
    public RoleResolverBindingProposal {
        if (nodeId == null || nodeId <= 0) throw new IllegalArgumentException("nodeId must be positive");
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(contractHash, "contractHash");
        roleCode = RoleDirectoryQuery.stableCode(roleCode, "roleCode");
        organizationId = RoleDirectoryQuery.required(organizationId, "organizationId", 100);
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(candidateMode, "candidateMode");
    }
}
