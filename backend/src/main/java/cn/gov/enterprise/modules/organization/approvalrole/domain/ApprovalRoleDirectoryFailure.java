package cn.gov.enterprise.modules.organization.approvalrole.domain;

public final class ApprovalRoleDirectoryFailure extends RuntimeException {
    private final ApprovalRoleDirectoryFailureCode code;
    public ApprovalRoleDirectoryFailure(ApprovalRoleDirectoryFailureCode code, String message) {
        super(message); this.code = code;
    }
    public ApprovalRoleDirectoryFailureCode code() { return code; }
}
