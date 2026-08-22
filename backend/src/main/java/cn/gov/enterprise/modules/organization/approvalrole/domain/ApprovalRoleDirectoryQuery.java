package cn.gov.enterprise.modules.organization.approvalrole.domain;

import java.time.Instant;
import java.util.Objects;

public record ApprovalRoleDirectoryQuery(String enterpriseId, long organizationId,
        ApprovalRoleCode roleCode, Instant effectiveAt) {
    public ApprovalRoleDirectoryQuery {
        enterpriseId = ApprovalRole.required(enterpriseId, "enterpriseId", 100);
        if (organizationId <= 0) throw new IllegalArgumentException("organizationId must be positive");
        Objects.requireNonNull(roleCode, "roleCode");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
    }
}
