package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.time.Instant;
import java.util.Objects;

/** Immutable ROLE Runtime Binding and minimum Directory evidence. */
public record RoleRuntimeBindingSnapshot(
        Long id,
        Long approvalId,
        Long bindingSetId,
        Long resolverBindingId,
        Long nodeResolverBindingId,
        Long instanceId,
        Long definitionVersionId,
        Long nodeId,
        ResolverCode resolverCode,
        ResolverVersion resolverVersion,
        ResolverContractHash contractHash,
        String roleCode,
        String organizationId,
        RoleRuntimeEvidence evidence,
        Instant effectiveAt,
        String bindingHash,
        Status status,
        String auditInfo,
        int version) {

    public enum Status { FROZEN, ARCHIVED, SECURITY_BLOCKED }

    public RoleRuntimeBindingSnapshot {
        positive(id, "id"); positive(approvalId, "approvalId");
        positive(bindingSetId, "bindingSetId"); positive(resolverBindingId, "resolverBindingId");
        positive(nodeResolverBindingId, "nodeResolverBindingId"); positive(instanceId, "instanceId");
        positive(definitionVersionId, "definitionVersionId"); positive(nodeId, "nodeId");
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(contractHash, "contractHash");
        roleCode = RoleDirectoryQuery.stableCode(roleCode, "roleCode");
        organizationId = RoleDirectoryQuery.required(organizationId, "organizationId", 100);
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        bindingHash = RoleCandidateResult.hash(bindingHash, "bindingHash");
        Objects.requireNonNull(status, "status");
        auditInfo = RoleDirectoryQuery.required(auditInfo, "auditInfo", 65535);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        String expected = RoleRuntimePersistencePolicy.hash(
                resolverCode, resolverVersion, contractHash, roleCode,
                organizationId, evidence, effectiveAt);
        if (!expected.equals(bindingHash)) {
            throw new IllegalArgumentException("ROLE Runtime persistence bindingHash mismatch");
        }
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
