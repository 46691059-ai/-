package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.util.Objects;

/** Fail-closed result of evaluating one ROLE proposal for possible future promotion. */
public record RoleRuntimeEligibility(
        ResolverCode resolverCode, ResolverVersion resolverVersion,
        ResolverContractHash contractHash, String bindingHash,
        String roleCode, String organizationId, Status status, String reason) {
    public enum Status { READY, BLOCKED, REJECTED }

    public RoleRuntimeEligibility {
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(contractHash, "contractHash");
        bindingHash = RoleCandidateResult.hash(bindingHash, "bindingHash");
        roleCode = RoleDirectoryQuery.stableCode(roleCode, "roleCode");
        organizationId = RoleDirectoryQuery.required(organizationId, "organizationId", 100);
        Objects.requireNonNull(status, "status");
        reason = RoleDirectoryQuery.required(reason, "reason", 300);
    }

    public boolean ready() { return status == Status.READY; }
}
