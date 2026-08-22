package cn.gov.enterprise.modules.system.menu.vo;

import java.time.LocalDateTime;
import java.util.List;

public record MenuVO(
        Long id,
        String menuCode,
        Long parentId,
        String menuName,
        String menuType,
        String path,
        String component,
        String permission,
        String icon,
        Integer sort,
        Integer visible,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        Integer version,
        List<MenuVO> children) {
}
