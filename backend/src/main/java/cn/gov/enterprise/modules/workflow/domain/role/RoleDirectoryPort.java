package cn.gov.enterprise.modules.workflow.domain.role;

/** Required-side port. Its real Organization/Governance adapter is deliberately absent. */
public interface RoleDirectoryPort {
    RoleDirectoryResult resolve(RoleDirectoryQuery query);
}
