package cn.gov.enterprise.modules.organization.approvalrole.domain;

public final class ApprovalRoleRevisionPolicy {
    public long next(long current) {
        if (current < 0 || current == Long.MAX_VALUE) throw new ApprovalRoleDirectoryFailure(
                ApprovalRoleDirectoryFailureCode.REVISION_INCONSISTENT, "revision cannot advance");
        return current + 1;
    }
}
