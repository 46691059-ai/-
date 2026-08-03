package cn.gov.enterprise.security;

import java.util.List;

public record LoginResponse(
        String token,
        UserInfo userInfo,
        List<String> roles,
        List<String> permissions,
        List<String> buttonPermissions,
        List<MenuItem> menus) {

    public record UserInfo(Long id, String username, String realName, Long orgId) {
    }

    public record MenuItem(
            Long id, Long parentId, String menuName, String menuType, String path,
            String component, String permission, String icon, Integer visible,
            List<MenuItem> children) {
    }
}
