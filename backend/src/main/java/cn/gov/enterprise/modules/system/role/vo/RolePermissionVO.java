package cn.gov.enterprise.modules.system.role.vo;

import java.util.List;

public record RolePermissionVO(Long roleId, List<Long> menuIds, List<String> permissionCodes) {
}
