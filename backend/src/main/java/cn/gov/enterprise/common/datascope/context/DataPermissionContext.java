package cn.gov.enterprise.common.datascope.context;

import java.util.Set;

/** 当前登录用户在一次受控查询中的数据权限快照。 */
public record DataPermissionContext(
        Long userId,
        String username,
        Long orgId,
        Set<Long> roleIds,
        DataScopeType dataScope,
        Set<Long> allowedOrgIds,
        boolean selfIncluded) {

    public DataPermissionContext {
        roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        allowedOrgIds = allowedOrgIds == null ? Set.of() : Set.copyOf(allowedOrgIds);
        dataScope = dataScope == null ? DataScopeType.SELF : dataScope;
    }

    public boolean unrestricted() {
        return dataScope == DataScopeType.ALL;
    }
}
