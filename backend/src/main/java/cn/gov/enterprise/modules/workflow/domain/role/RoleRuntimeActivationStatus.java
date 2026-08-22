package cn.gov.enterprise.modules.workflow.domain.role;

/** Governance lifecycle. ENABLED is reserved and deliberately unreachable in WF5.8. */
public enum RoleRuntimeActivationStatus {
    DRAFT,
    ELIGIBLE,
    APPROVED,
    ENABLED,
    BLOCKED,
    REJECTED,
    REVOKED
}
