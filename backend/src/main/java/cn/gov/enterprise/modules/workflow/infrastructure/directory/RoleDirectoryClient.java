package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryQuery;

/** Remote transport boundary only; validation belongs to the production adapter. */
@FunctionalInterface
public interface RoleDirectoryClient {
    RoleDirectoryTransportResponse fetch(RoleDirectoryQuery query);
}
