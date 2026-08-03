package cn.gov.enterprise.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuthRouteService {
    private final SecurityIdentityMapper identityMapper;
    private final PermissionCacheService permissionCacheService;

    public AuthRouteService(SecurityIdentityMapper identityMapper, PermissionCacheService permissionCacheService) {
        this.identityMapper = identityMapper;
        this.permissionCacheService = permissionCacheService;
    }

    public List<LoginResponse.MenuItem> routes(Long userId) {
        List<SecurityIdentityMapper.SecurityMenuRow> rows = permissionCacheService.menus(
                userId, () -> identityMapper.selectMenus(userId));
        Map<Long, LoginResponse.MenuItem> nodes = new LinkedHashMap<>();
        for (SecurityIdentityMapper.SecurityMenuRow row : rows) {
            if (!"B".equals(row.menuType()) && !Integer.valueOf(0).equals(row.visible())) {
                nodes.put(row.id(), new LoginResponse.MenuItem(row.id(), row.parentId(), row.menuName(), row.menuType(),
                        row.path(), row.component(), row.permission(), row.icon(), row.visible(), new ArrayList<>()));
            }
        }
        List<LoginResponse.MenuItem> roots = new ArrayList<>();
        for (LoginResponse.MenuItem node : nodes.values()) {
            LoginResponse.MenuItem parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent == null) roots.add(node); else parent.children().add(node);
        }
        return roots;
    }
}
