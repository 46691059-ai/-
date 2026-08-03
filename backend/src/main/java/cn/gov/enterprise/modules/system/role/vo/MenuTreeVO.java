package cn.gov.enterprise.modules.system.role.vo;

import java.util.List;

public record MenuTreeVO(
        Long id, String menuName, String menuType, Long parentId,
        String permission, String path, Integer status, List<MenuTreeVO> children) {
}
