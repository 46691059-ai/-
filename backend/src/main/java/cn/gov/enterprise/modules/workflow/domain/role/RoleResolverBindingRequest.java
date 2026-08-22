package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;

/** Input used to calculate, but not persist, one node-level ROLE proposal. */
public record RoleResolverBindingRequest(
        Long nodeId, String roleCode, String organizationId, Instant effectiveAt) {
    public RoleResolverBindingRequest {
        if (nodeId == null || nodeId <= 0) throw new IllegalArgumentException("nodeId must be positive");
        roleCode = RoleDirectoryQuery.stableCode(roleCode, "roleCode");
        organizationId = RoleDirectoryQuery.required(organizationId, "organizationId", 100);
        if (effectiveAt == null) throw new IllegalArgumentException("effectiveAt must not be null");
    }
}
