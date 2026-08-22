package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;
import java.time.Instant;
import java.util.Objects;

/** Runtime facts required by the prepared ROLE resolver. */
public record RoleResolverContext(
        Long instanceId, Long nodeExecutionId, ResolverVersionBinding resolverBinding,
        String roleCode, String businessOrgId, String enterpriseId,
        Instant effectiveAt, String traceId) {
    public RoleResolverContext {
        positive(instanceId, "instanceId");
        positive(nodeExecutionId, "nodeExecutionId");
        Objects.requireNonNull(resolverBinding, "resolverBinding");
        if (!instanceId.equals(resolverBinding.instanceId())) {
            throw new IllegalArgumentException("resolver binding belongs to another instance");
        }
        roleCode = RoleDirectoryQuery.stableCode(roleCode, "roleCode");
        businessOrgId = RoleDirectoryQuery.required(businessOrgId, "businessOrgId", 100);
        enterpriseId = RoleDirectoryQuery.required(enterpriseId, "enterpriseId", 100);
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        traceId = RoleDirectoryQuery.required(traceId, "traceId", 100);
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
