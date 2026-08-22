package cn.gov.enterprise.modules.workflow.domain.role;

/** Governed evidence sources; priority never resolves a business conflict. */
public enum RoleDirectorySourceType {
    MANUAL_GOVERNANCE,
    HR_SYNC,
    EXTERNAL_SYNC
}
