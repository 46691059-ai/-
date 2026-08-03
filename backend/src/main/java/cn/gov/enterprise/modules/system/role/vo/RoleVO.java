package cn.gov.enterprise.modules.system.role.vo;

import java.time.LocalDateTime;

public record RoleVO(
        Long id, String roleName, String roleCode, String description,
        String dataScopeType, Integer status, long userCount,
        LocalDateTime createTime, LocalDateTime updateTime, Integer version) {
}
