package cn.gov.enterprise.modules.system.menu.service.impl;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.system.entity.SysMenuEntity;
import cn.gov.enterprise.modules.system.mapper.SysMenuMapper;
import cn.gov.enterprise.modules.system.mapper.SysPermissionMapper;
import cn.gov.enterprise.modules.system.entity.SysPermissionEntity;
import cn.gov.enterprise.modules.system.menu.dto.MenuCreateRequest;
import cn.gov.enterprise.modules.system.menu.dto.MenuPageQuery;
import cn.gov.enterprise.modules.system.menu.dto.MenuUpdateRequest;
import cn.gov.enterprise.modules.system.menu.mapper.MenuManagementMapper;
import cn.gov.enterprise.modules.system.menu.service.MenuManagementService;
import cn.gov.enterprise.modules.system.menu.vo.MenuVO;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.PermissionCacheService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MenuManagementServiceImpl implements MenuManagementService {
    private static final Set<String> MENU_TYPES = Set.of("M", "C", "B");
    private final SysMenuMapper menuMapper;
    private final MenuManagementMapper managementMapper;
    private final SysPermissionMapper permissionMapper;
    private final CurrentSecurityContext securityContext;
    private final PermissionCacheService permissionCacheService;

    public MenuManagementServiceImpl(
            SysMenuMapper menuMapper,
            MenuManagementMapper managementMapper,
            SysPermissionMapper permissionMapper,
            CurrentSecurityContext securityContext,
            PermissionCacheService permissionCacheService) {
        this.menuMapper = menuMapper;
        this.managementMapper = managementMapper;
        this.permissionMapper = permissionMapper;
        this.securityContext = securityContext;
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    public List<MenuVO> tree() {
        List<SysMenuEntity> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                .orderByAsc(SysMenuEntity::getSortNo).orderByAsc(SysMenuEntity::getId));
        Map<Long, MenuVO> nodes = new LinkedHashMap<>();
        for (SysMenuEntity menu : menus) nodes.put(menu.getId(), toVO(menu, new ArrayList<>()));
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO node : nodes.values()) {
            MenuVO parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent == null) roots.add(node); else parent.children().add(node);
        }
        return roots;
    }

    @Override
    public PageResponse<MenuVO> page(MenuPageQuery query) {
        String type = normalizeType(query.menuType(), false);
        Page<SysMenuEntity> result = menuMapper.selectPage(new Page<>(query.currentPage(), query.pageSize()),
                new LambdaQueryWrapper<SysMenuEntity>()
                        .like(StringUtils.hasText(query.menuCode()), SysMenuEntity::getMenuCode, trim(query.menuCode()))
                        .like(StringUtils.hasText(query.menuName()), SysMenuEntity::getMenuName, trim(query.menuName()))
                        .eq(type != null, SysMenuEntity::getMenuType, type)
                        .like(StringUtils.hasText(query.permission()), SysMenuEntity::getPermission, trim(query.permission()))
                        .eq(query.status() != null, SysMenuEntity::getStatus, query.status())
                        .orderByAsc(SysMenuEntity::getSortNo).orderByAsc(SysMenuEntity::getId));
        return new PageResponse<>(result.getRecords().stream().map(menu -> toVO(menu, List.of())).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional
    public Long create(MenuCreateRequest request) {
        String type = normalizeType(request.menuType(), true);
        validateParent(request.parentId(), type, null);
        assertMenuCodeUnique(request.menuCode(), null);
        assertPermissionResourceExists(request.permission());
        SysMenuEntity menu = new SysMenuEntity();
        menu.setMenuCode(trim(request.menuCode()));
        apply(menu, request.parentId(), request.menuName(), type, request.path(), request.component(),
                request.permission(), request.icon(), request.sort(), request.status());
        menuMapper.insert(menu);
        return menu.getId();
    }

    @Override
    @Transactional
    public void update(MenuUpdateRequest request) {
        SysMenuEntity menu = requireMenu(request.id());
        if (!Objects.equals(menu.getMenuCode(), trim(request.menuCode()))) {
            throw new BusinessException("B0409", "菜单业务编码创建后不可修改");
        }
        String type = normalizeType(request.menuType(), true);
        validateParent(request.parentId(), type, request.id());
        assertPermissionResourceExists(request.permission());
        if (managementMapper.countRoleBindings(request.id()) > 0
                && !Objects.equals(blankToNull(menu.getPermission()), blankToNull(request.permission()))) {
            throw new BusinessException("B0409", "已绑定角色的菜单不能修改权限标识，请先解除角色授权");
        }
        if ("B".equals(type) && menuMapper.selectCount(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getParentId, request.id())) > 0) {
            throw new BusinessException("B0409", "存在子菜单的节点不能修改为按钮类型");
        }
        menu.setVersion(request.version());
        apply(menu, request.parentId(), request.menuName(), type, request.path(), request.component(),
                request.permission(), request.icon(), request.sort(), request.status());
        List<Long> affectedUsers = managementMapper.selectBoundUserIds(menu.getId());
        if (menuMapper.updateById(menu) != 1) {
            throw new BusinessException("B0409", "菜单数据已被修改，请刷新后重试");
        }
        affectedUsers.forEach(permissionCacheService::evict);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysMenuEntity menu = requireMenu(id);
        if (menuMapper.selectCount(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getParentId, id)) > 0) {
            throw new BusinessException("B0409", "存在子菜单，禁止删除");
        }
        if (managementMapper.countRoleBindings(id) > 0) {
            throw new BusinessException("B0409", "菜单已绑定角色，禁止删除");
        }
        menu.setDeleteToken(menu.getId());
        menu.setUpdateBy(securityContext.username());
        if (menuMapper.updateById(menu) != 1 || menuMapper.deleteById(id) != 1) {
            throw new BusinessException("B0409", "菜单删除失败，请刷新后重试");
        }
    }

    private void apply(SysMenuEntity menu, Long parentId, String name, String type, String path,
                       String component, String permission, String icon, Integer sort, Integer status) {
        menu.setParentId(parentId);
        menu.setMenuName(trim(name));
        menu.setMenuType(type);
        menu.setPath("B".equals(type) ? null : blankToNull(path));
        menu.setComponent("B".equals(type) ? null : blankToNull(component));
        menu.setPermission(blankToNull(permission));
        menu.setIcon(blankToNull(icon));
        menu.setSortNo(sort);
        menu.setVisible("B".equals(type) ? 0 : 1);
        menu.setStatus(status);
        if ("C".equals(type) && !StringUtils.hasText(menu.getPath())) {
            throw new BusinessException("B0409", "菜单类型C必须配置路由地址");
        }
        if ("B".equals(type) && !StringUtils.hasText(menu.getPermission())) {
            throw new BusinessException("B0409", "按钮类型B必须配置权限标识");
        }
    }

    private void validateParent(Long parentId, String type, Long currentId) {
        if (parentId == null) {
            if ("B".equals(type)) throw new BusinessException("B0409", "按钮必须选择上级菜单");
            return;
        }
        if (parentId.equals(currentId)) throw new BusinessException("B0409", "菜单不能选择自身作为上级");
        SysMenuEntity parent = requireMenu(parentId);
        if ("B".equals(parent.getMenuType())) throw new BusinessException("B0409", "按钮节点不能作为上级菜单");
        if (currentId != null && isDescendant(parent, currentId)) {
            throw new BusinessException("B0409", "不能将菜单移动到自身下级节点");
        }
    }

    private boolean isDescendant(SysMenuEntity node, Long ancestorId) {
        SysMenuEntity current = node;
        while (current != null && current.getParentId() != null) {
            if (ancestorId.equals(current.getParentId())) return true;
            current = menuMapper.selectById(current.getParentId());
        }
        return false;
    }

    private void assertMenuCodeUnique(String menuCode, Long excludedId) {
        long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getMenuCode, trim(menuCode))
                .ne(excludedId != null, SysMenuEntity::getId, excludedId));
        if (count > 0) throw new BusinessException("B0409", "菜单业务编码已存在");
    }

    private void assertPermissionResourceExists(String permission) {
        if (!StringUtils.hasText(permission)) return;
        long count = permissionMapper.selectCount(new LambdaQueryWrapper<SysPermissionEntity>()
                .eq(SysPermissionEntity::getPermissionCode, trim(permission))
                .eq(SysPermissionEntity::getStatus, 1));
        if (count == 0) throw new BusinessException("B0409", "权限标识未在权限资源中登记或已停用");
    }

    private SysMenuEntity requireMenu(Long id) {
        SysMenuEntity menu = menuMapper.selectById(id);
        if (menu == null) throw new BusinessException("B0404", "菜单不存在");
        return menu;
    }

    private String normalizeType(String value, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) throw new BusinessException("B0409", "菜单类型不能为空");
            return null;
        }
        String type = value.trim().toUpperCase();
        if (!MENU_TYPES.contains(type)) throw new BusinessException("B0409", "菜单类型仅支持M、C、B");
        return type;
    }

    private MenuVO toVO(SysMenuEntity menu, List<MenuVO> children) {
        return new MenuVO(menu.getId(), menu.getMenuCode(), menu.getParentId(), menu.getMenuName(), menu.getMenuType(), menu.getPath(),
                menu.getComponent(), menu.getPermission(), menu.getIcon(), menu.getSortNo(), menu.getVisible(),
                menu.getStatus(), menu.getCreateTime(), menu.getUpdateTime(), menu.getVersion(), children);
    }

    private String trim(String value) { return value == null ? null : value.trim(); }
    private String blankToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
