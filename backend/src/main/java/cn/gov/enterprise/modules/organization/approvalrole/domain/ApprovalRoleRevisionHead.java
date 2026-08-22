package cn.gov.enterprise.modules.organization.approvalrole.domain;

public record ApprovalRoleRevisionHead(long id, String enterpriseId, long organizationId,
        ApprovalRoleCode roleCode, long currentRevision, String currentResultHash, int version) {
    public ApprovalRoleRevisionHead {
        if (id <= 0 || organizationId <= 0 || currentRevision < 0 || version < 0) {
            throw new IllegalArgumentException("invalid revision head");
        }
        enterpriseId = ApprovalRole.required(enterpriseId, "enterpriseId", 100);
        if (roleCode == null) throw new IllegalArgumentException("roleCode must not be null");
        if (currentRevision > 0) ApprovalRoleAssignment.hash(currentResultHash, "currentResultHash");
    }
}
