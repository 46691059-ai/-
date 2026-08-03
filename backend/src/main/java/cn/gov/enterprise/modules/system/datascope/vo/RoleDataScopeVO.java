package cn.gov.enterprise.modules.system.datascope.vo;

import java.util.List;

/** 角色当前数据范围配置。 */
public record RoleDataScopeVO(
        Long roleId,
        String dataScope,
        List<Long> orgIds) {
}
