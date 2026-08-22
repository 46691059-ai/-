package cn.gov.enterprise.modules.organization.approvalrole.domain;

public enum ApprovalRoleAssignmentSourceType {
    HR_ASSIGNMENT, GOVERNANCE_DECISION, MANUAL_GOVERNANCE_RECORD, IMPORT;

    public String workflowSourceType() {
        return switch (this) {
            case HR_ASSIGNMENT -> "HR_SYNC";
            case GOVERNANCE_DECISION, MANUAL_GOVERNANCE_RECORD -> "MANUAL_GOVERNANCE";
            case IMPORT -> "EXTERNAL_SYNC";
        };
    }
}
