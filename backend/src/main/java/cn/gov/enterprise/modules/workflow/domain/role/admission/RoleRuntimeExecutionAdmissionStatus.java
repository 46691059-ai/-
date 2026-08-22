package cn.gov.enterprise.modules.workflow.domain.role.admission;

/** Governance state only. No state in this enum enables ROLE runtime. */
public enum RoleRuntimeExecutionAdmissionStatus {
    CREATED,
    VALIDATING,
    ELIGIBLE,
    APPROVED_FOR_EXECUTION,
    BLOCKED,
    REJECTED,
    REVOKED,
    EXPIRED
}
