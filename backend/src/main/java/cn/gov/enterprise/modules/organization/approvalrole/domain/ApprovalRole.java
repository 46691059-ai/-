package cn.gov.enterprise.modules.organization.approvalrole.domain;

import java.time.Instant;
import java.util.Objects;

public record ApprovalRole(long id, String enterpriseId, ApprovalRoleCode roleCode,
        String roleName, String roleType, String organizationScopeType,
        ApprovalRoleStatus status, String description, String createdBy,
        Instant createdTime, String updatedBy, Instant updatedTime, int version, long deleteToken) {
    public static final String ROLE_TYPE = "PROCESS_APPROVAL_ROLE";
    public static final String SCOPE_TYPE = "BUSINESS_ORG";

    public ApprovalRole {
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        enterpriseId = required(enterpriseId, "enterpriseId", 100);
        Objects.requireNonNull(roleCode, "roleCode");
        roleName = required(roleName, "roleName", 200);
        if (!ROLE_TYPE.equals(roleType)) throw new IllegalArgumentException("unsupported roleType");
        if (!SCOPE_TYPE.equals(organizationScopeType)) throw new IllegalArgumentException("unsupported organizationScopeType");
        Objects.requireNonNull(status, "status");
        createdBy = required(createdBy, "createdBy", 64);
        updatedBy = required(updatedBy, "updatedBy", 64);
        Objects.requireNonNull(createdTime, "createdTime");
        Objects.requireNonNull(updatedTime, "updatedTime");
        if (version < 0 || deleteToken < 0) throw new IllegalArgumentException("invalid governance version");
    }

    public static ApprovalRole active(long id, String enterpriseId, String roleCode,
            String roleName, String description, String actor, Instant now) {
        return new ApprovalRole(id, enterpriseId, new ApprovalRoleCode(roleCode), roleName,
                ROLE_TYPE, SCOPE_TYPE, ApprovalRoleStatus.ACTIVE, description, actor, now, actor, now, 0, 0);
    }

    public ApprovalRole withStatus(ApprovalRoleStatus next, String actor, Instant now) {
        if (status == next) return this;
        return new ApprovalRole(id, enterpriseId, roleCode, roleName, roleType,
                organizationScopeType, next, description, createdBy, createdTime,
                actor, now, version + 1, deleteToken);
    }

    static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
