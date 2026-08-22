package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.time.Instant;
import java.util.Objects;

/** Fail-closed policy that freezes an approved ROLE runtime persistence snapshot. */
public final class RoleRuntimePersistencePolicy {
    public static final String CANONICAL_VERSION = "ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1";

    private RoleRuntimePersistencePolicy() { }

    public static RoleRuntimeBindingSnapshot freeze(
            Long id, RoleRuntimeApproval approval, Long bindingSetId,
            Long resolverBindingId, Long nodeResolverBindingId, Long instanceId,
            Long definitionVersionId, Long nodeId, String roleCode,
            String organizationId, RoleRuntimeEvidence evidence, Instant effectiveAt,
            String auditInfo) {
        Objects.requireNonNull(approval, "approval");
        if (!approval.approved()) {
            throw new IllegalStateException("ROLE Runtime Binding requires APPROVED governance evidence");
        }
        String hash = hash(approval.resolverCode(), approval.resolverVersion(),
                approval.contractHash(), roleCode, organizationId, evidence, effectiveAt);
        return new RoleRuntimeBindingSnapshot(id, approval.id(), bindingSetId,
                resolverBindingId, nodeResolverBindingId, instanceId,
                definitionVersionId, nodeId, approval.resolverCode(),
                approval.resolverVersion(), approval.contractHash(), roleCode,
                organizationId, evidence, effectiveAt, hash,
                RoleRuntimeBindingSnapshot.Status.FROZEN, auditInfo, 0);
    }

    public static String hash(
            ResolverCode resolverCode, ResolverVersion resolverVersion,
            ResolverContractHash contractHash, String roleCode,
            String organizationId, RoleRuntimeEvidence evidence, Instant effectiveAt) {
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(contractHash, "contractHash");
        String normalizedRole = RoleDirectoryQuery.stableCode(roleCode, "roleCode");
        String normalizedOrg = RoleDirectoryQuery.required(organizationId, "organizationId", 100);
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        String canonical = "{\"candidateRuleHash\":\"" + evidence.candidateRuleHash()
                + "\",\"contractHash\":\"" + contractHash.value()
                + "\",\"directoryHash\":\"" + evidence.directoryHash()
                + "\",\"directoryRevision\":" + evidence.directoryRevision()
                + ",\"effectiveAt\":\"" + effectiveAt
                + "\",\"organizationId\":\"" + escape(normalizedOrg)
                + "\",\"resolverCode\":\"" + resolverCode.value()
                + "\",\"resolverVersion\":\"" + resolverVersion.value()
                + "\",\"roleCode\":\"" + escape(normalizedRole)
                + "\",\"roleRuleHash\":\"" + evidence.roleRuleHash()
                + "\",\"schema\":\"" + CANONICAL_VERSION
                + "\",\"sourceEvidenceHash\":\"" + evidence.sourceEvidenceHash()
                + "\"}";
        return RoleDirectoryCanonical.sha256(canonical);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
