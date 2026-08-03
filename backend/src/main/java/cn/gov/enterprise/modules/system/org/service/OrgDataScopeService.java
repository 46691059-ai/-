package cn.gov.enterprise.modules.system.org.service;

import java.util.List;

/** 为后续数据权限计算提供统一组织关系能力。 */
public interface OrgDataScopeService {
    Long getUserOrgId(Long userId);
    List<Long> getChildrenOrgIds(Long orgId);
    List<Long> getParentOrgIds(Long orgId);

    default List<Long> getChildrenOrgIdsByUser(Long userId) {
        return getChildrenOrgIds(getUserOrgId(userId));
    }

    default List<Long> getParentOrgIdsByUser(Long userId) {
        return getParentOrgIds(getUserOrgId(userId));
    }
}
