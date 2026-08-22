package cn.gov.enterprise.modules.workflow.domain.role.persistence;

/** Snapshot governance status. ACTIVE never means Resolver or ROLE Runtime enabled. */
public enum RoleRuntimeBindingLifecycleStatus {
    CREATED,
    VALIDATED,
    ACTIVE,
    BLOCKED,
    REVOKED
}
