package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;
import java.time.Instant;
import java.util.Objects;

/** Frozen context for a ROLE candidate preview. */
public record RoleCandidateContext(
        Long instanceId, Long nodeExecutionId, String roleCode, String organizationId,
        Instant effectiveAt, ResolverVersionBinding resolverBinding) {
    public RoleCandidateContext {
        positive(instanceId, "instanceId");
        positive(nodeExecutionId, "nodeExecutionId");
        roleCode = RoleDirectoryQuery.stableCode(roleCode, "roleCode");
        organizationId = RoleDirectoryQuery.required(organizationId, "organizationId", 100);
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(resolverBinding, "resolverBinding");
        if (!instanceId.equals(resolverBinding.instanceId())) {
            throw new IllegalArgumentException("resolver binding belongs to another instance");
        }
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
