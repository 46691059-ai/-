package cn.gov.enterprise.modules.system.role.service.impl;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.system.entity.SysMenuEntity;
import cn.gov.enterprise.modules.system.entity.SysPermissionEntity;
import cn.gov.enterprise.modules.system.entity.SysRoleEntity;
import cn.gov.enterprise.modules.system.entity.SysRoleMenuEntity;
import cn.gov.enterprise.modules.system.entity.SysRolePermissionEntity;
import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import cn.gov.enterprise.modules.system.mapper.SysMenuMapper;
import cn.gov.enterprise.modules.system.mapper.SysPermissionMapper;
import cn.gov.enterprise.modules.system.mapper.SysRoleMapper;
import cn.gov.enterprise.modules.system.mapper.SysRoleMenuMapper;
import cn.gov.enterprise.modules.system.mapper.SysRolePermissionMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import cn.gov.enterprise.modules.system.role.dto.RoleCreateRequest;
import cn.gov.enterprise.modules.system.role.dto.RoleMenuAssignRequest;
import cn.gov.enterprise.modules.system.role.dto.RolePageQuery;
import cn.gov.enterprise.modules.system.role.dto.RoleUpdateRequest;
import cn.gov.enterprise.modules.system.role.mapper.RoleManagementMapper;
import cn.gov.enterprise.modules.system.role.service.RoleManagementService;
import cn.gov.enterprise.modules.system.role.vo.MenuTreeVO;
import cn.gov.enterprise.modules.system.role.vo.RolePermissionVO;
import cn.gov.enterprise.modules.system.role.vo.RoleVO;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.PermissionCacheService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RoleManagementServiceImpl implements RoleManagementService {
    private static final Set<String> DATA_SCOPES = Set.of("ALL", "ORG", "ORG_AND_CHILDREN", "SELF", "CUSTOM");
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserMapper userMapper;
    private final RoleManagementMapper managementMapper;
    private final CurrentSecurityContext securityContext;
    private final PermissionCacheService permissionCacheService;

    public RoleManagementServiceImpl(
            SysRoleMapper roleMapper,
            SysMenuMapper menuMapper,
            SysPermissionMapper permissionMapper,
            SysRoleMenuMapper roleMenuMapper,
            SysRolePermissionMapper rolePermissionMapper,
            SysUserMapper userMapper,
            RoleManagementMapper managementMapper,
            CurrentSecurityContext securityContext,
            PermissionCacheService permissionCacheService) {
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.permissionMapper = permissionMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userMapper = userMapper;
        this.managementMapper = managementMapper;
        this.securityContext = securityContext;
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    public PageResponse<RoleVO> page(RolePageQuery query) {
        LambdaQueryWrapper<SysRoleEntity> wrapper = new LambdaQueryWrapper<SysRoleEntity>()
                .like(StringUtils.hasText(query.roleName()), SysRoleEntity::getRoleName, trim(query.roleName()))
                .like(StringUtils.hasText(query.roleCode()), SysRoleEntity::getRoleCode, normalizeCode(query.roleCode()))
                .eq(query.status() != null, SysRoleEntity::getStatus, query.status())
                .orderByAsc(SysRoleEntity::getRoleName);
        Page<SysRoleEntity> result = roleMapper.selectPage(new Page<>(query.currentPage(), query.pageSize()), wrapper);
        return new PageResponse<>(toVos(result.getRecords()), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public RoleVO detail(Long id) {
        return toVos(List.of(requireRole(id))).getFirst();
    }

    @Override
    @Transactional
    public Long create(RoleCreateRequest request) {
        String code = normalizeCode(request.roleCode());
        assertRoleCodeUnique(code, null);
        SysRoleEntity role = new SysRoleEntity();
        role.setRoleName(trim(request.roleName()));
        role.setRoleCode(code);
        role.setDescription(blankToNull(request.description()));
        String initialDataScope = requireDataScope(request.dataScopeType());
        if ("CUSTOM".equals(initialDataScope)) {
            throw new BusinessException("B0409", "请先创建角色，再通过数据权限配置入口设置CUSTOM组织范围");
        }
        role.setDataScopeType(initialDataScope);
        role.setStatus(request.status());
        roleMapper.insert(role);
        return role.getId();
    }

    @Override
    @Transactional
    public void update(RoleUpdateRequest request) {
        SysRoleEntity role = requireRole(request.id());
        assertNotProtected(role);
        String requestedDataScope = requireDataScope(request.dataScopeType());
        if (!Objects.equals(role.getDataScopeType(), requestedDataScope)) {
            throw new BusinessException("B0409", "请通过数据权限配置入口修改角色数据范围");
        }
        String code = normalizeCode(request.roleCode());
        assertRoleCodeUnique(code, request.id());
        role.setVersion(request.version());
        role.setRoleName(trim(request.roleName()));
        role.setRoleCode(code);
        role.setDescription(blankToNull(request.description()));
        role.setDataScopeType(requestedDataScope);
        role.setStatus(request.status());
        invalidateRoleUsers(role.getId());
        if (roleMapper.updateById(role) != 1) {
            throw new BusinessException("B0409", "角色数据已被修改，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysRoleEntity role = requireRole(id);
        assertNotProtected(role);
        if (managementMapper.countUsersByRole(id) > 0) {
            throw new BusinessException("B0409", "角色已分配给用户，禁止删除");
        }
        managementMapper.logicallyDeleteRoleMenus(id, securityContext.username());
        managementMapper.logicallyDeleteRolePermissions(id, securityContext.username());
        role.setDeleteToken(role.getId());
        if (roleMapper.updateById(role) != 1 || roleMapper.deleteById(id) != 1) {
            throw new BusinessException("B0409", "角色删除失败，请刷新后重试");
        }
    }

    @Override
    public List<MenuTreeVO> menuTree() {
        List<SysMenuEntity> menus = activeMenus();
        Map<Long, MenuTreeVO> nodes = new LinkedHashMap<>();
        for (SysMenuEntity menu : menus) {
            nodes.put(menu.getId(), new MenuTreeVO(menu.getId(), menu.getMenuName(), menu.getMenuType(),
                    menu.getParentId(), menu.getPermission(), menu.getPath(), menu.getStatus(), new ArrayList<>()));
        }
        List<MenuTreeVO> roots = new ArrayList<>();
        for (MenuTreeVO node : nodes.values()) {
            MenuTreeVO parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent == null) roots.add(node); else parent.children().add(node);
        }
        return roots;
    }

    @Override
    public RolePermissionVO rolePermissions(Long roleId) {
        requireRole(roleId);
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenuEntity>()
                        .eq(SysRoleMenuEntity::getRoleId, roleId))
                .stream().map(SysRoleMenuEntity::getMenuId).toList();
        List<Long> permissionIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<SysRolePermissionEntity>()
                                .eq(SysRolePermissionEntity::getRoleId, roleId))
                .stream().map(SysRolePermissionEntity::getPermissionId).toList();
        List<String> permissionCodes = permissionIds.isEmpty() ? List.of()
                : permissionMapper.selectBatchIds(permissionIds).stream()
                        .map(SysPermissionEntity::getPermissionCode).sorted().toList();
        return new RolePermissionVO(roleId, menuIds, permissionCodes);
    }

    @Override
    @Transactional
    public void assignMenus(RoleMenuAssignRequest request) {
        SysRoleEntity role = requireRole(request.roleId());
        assertNotProtected(role);
        Map<Long, SysMenuEntity> menuById = activeMenus().stream()
                .collect(Collectors.toMap(SysMenuEntity::getId, Function.identity()));
        Set<Long> selectedIds = new LinkedHashSet<>(request.menuIds());
        if (!menuById.keySet().containsAll(selectedIds)) {
            throw new BusinessException("B0409", "包含不存在或已停用的菜单");
        }
        Set<Long> expandedIds = new LinkedHashSet<>(selectedIds);
        for (Long menuId : selectedIds) {
            SysMenuEntity menu = menuById.get(menuId);
            while (menu != null && menu.getParentId() != null) {
                expandedIds.add(menu.getParentId());
                menu = menuById.get(menu.getParentId());
            }
        }
        Set<String> permissionCodes = expandedIds.stream().map(menuById::get).filter(Objects::nonNull)
                .map(SysMenuEntity::getPermission).filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SysPermissionEntity> permissions = permissionCodes.isEmpty() ? List.of()
                : permissionMapper.selectList(new LambdaQueryWrapper<SysPermissionEntity>()
                        .in(SysPermissionEntity::getPermissionCode, permissionCodes)
                        .eq(SysPermissionEntity::getStatus, 1));
        if (permissions.size() != permissionCodes.size()) {
            throw new BusinessException("B0409", "菜单关联的权限资源不存在或已停用");
        }

        invalidateRoleUsers(role.getId());
        managementMapper.logicallyDeleteRoleMenus(role.getId(), securityContext.username());
        managementMapper.logicallyDeleteRolePermissions(role.getId(), securityContext.username());
        for (Long menuId : expandedIds) {
            SysRoleMenuEntity relation = new SysRoleMenuEntity();
            relation.setRoleId(role.getId());
            relation.setMenuId(menuId);
            roleMenuMapper.insert(relation);
        }
        for (SysPermissionEntity permission : permissions) {
            SysRolePermissionEntity relation = new SysRolePermissionEntity();
            relation.setRoleId(role.getId());
            relation.setPermissionId(permission.getId());
            rolePermissionMapper.insert(relation);
        }
    }

    private List<SysMenuEntity> activeMenus() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getStatus, 1)
                .orderByAsc(SysMenuEntity::getSortNo)
                .orderByAsc(SysMenuEntity::getMenuName));
    }

    private List<RoleVO> toVos(List<SysRoleEntity> roles) {
        if (roles.isEmpty()) return List.of();
        List<Long> ids = roles.stream().map(SysRoleEntity::getId).toList();
        Map<Long, Long> counts = managementMapper.countUsersByRoles(ids).stream()
                .collect(Collectors.toMap(RoleManagementMapper.RoleUserCountRow::roleId,
                        RoleManagementMapper.RoleUserCountRow::userCount));
        return roles.stream().map(role -> new RoleVO(role.getId(), role.getRoleName(), role.getRoleCode(),
                role.getDescription(), role.getDataScopeType(), role.getStatus(), counts.getOrDefault(role.getId(), 0L),
                role.getCreateTime(), role.getUpdateTime(), role.getVersion())).toList();
    }

    private SysRoleEntity requireRole(Long id) {
        SysRoleEntity role = roleMapper.selectById(id);
        if (role == null) throw new BusinessException("B0404", "角色不存在");
        return role;
    }

    private void assertNotProtected(SysRoleEntity role) {
        if (SUPER_ADMIN.equals(role.getRoleCode())) {
            throw new BusinessException("B0409", "超级管理员角色为系统保护角色，不允许修改");
        }
    }

    private void assertRoleCodeUnique(String code, Long excludedId) {
        LambdaQueryWrapper<SysRoleEntity> wrapper = new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getRoleCode, code)
                .ne(excludedId != null, SysRoleEntity::getId, excludedId);
        if (roleMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("B0409", "角色编码已存在");
        }
    }

    private void invalidateRoleUsers(Long roleId) {
        for (Long userId : managementMapper.selectUserIdsByRole(roleId)) {
            SysUserEntity user = userMapper.selectById(userId);
            if (user != null) {
                permissionCacheService.evict(userId);
                user.setTokenVersion(user.getTokenVersion() == null ? 1 : user.getTokenVersion() + 1);
                if (userMapper.updateById(user) != 1) {
                    throw new BusinessException("B0409", "角色关联用户已被修改，请刷新后重试");
                }
            }
        }
    }

    private String requireDataScope(String value) {
        String normalized = normalizeCode(value);
        if (!DATA_SCOPES.contains(normalized)) {
            throw new BusinessException("B0409", "数据范围仅支持ALL、ORG、ORG_AND_CHILDREN、SELF、CUSTOM");
        }
        return normalized;
    }

    private String normalizeCode(String value) { return value == null ? null : value.trim().toUpperCase(); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String blankToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
