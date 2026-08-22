package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

public record RoleDirectoryRaciApproval(
        boolean directoryOwnerConfirmed, boolean workflowOwnerConfirmed,
        boolean securityAuditConfirmed, boolean releaseApproverConfirmed) {
    public boolean complete() {
        return directoryOwnerConfirmed && workflowOwnerConfirmed
                && securityAuditConfirmed && releaseApproverConfirmed;
    }
}
