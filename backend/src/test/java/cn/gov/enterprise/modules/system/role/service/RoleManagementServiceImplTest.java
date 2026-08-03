package cn.gov.enterprise.modules.system.role.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import cn.gov.enterprise.modules.system.role.dto.RoleUpdateRequest;
import cn.gov.enterprise.modules.system.role.mapper.RoleManagementMapper;
import cn.gov.enterprise.modules.system.role.service.impl.RoleManagementServiceImpl;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.PermissionCacheService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceImplTest {
    @Mock SysRoleMapper roleMapper;
    @Mock SysMenuMapper menuMapper;
    @Mock SysPermissionMapper permissionMapper;
    @Mock SysRoleMenuMapper roleMenuMapper;
    @Mock SysRolePermissionMapper rolePermissionMapper;
    @Mock SysUserMapper userMapper;
    @Mock RoleManagementMapper managementMapper;
    @Mock CurrentSecurityContext securityContext;
    @Mock PermissionCacheService permissionCacheService;
    private RoleManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "role-management-test"),
                SysPermissionEntity.class);
        service = new RoleManagementServiceImpl(roleMapper, menuMapper, permissionMapper, roleMenuMapper,
                rolePermissionMapper, userMapper, managementMapper, securityContext, permissionCacheService);
    }

    @Test
    void createNormalizesRoleCodeAndDataScope() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        service.create(new RoleCreateRequest("财务负责人", " finance_manager ", null, "org_and_children", 1));
        ArgumentCaptor<SysRoleEntity> captor = ArgumentCaptor.forClass(SysRoleEntity.class);
        verify(roleMapper).insert(captor.capture());
        assertThat(captor.getValue().getRoleCode()).isEqualTo("FINANCE_MANAGER");
        assertThat(captor.getValue().getDataScopeType()).isEqualTo("ORG_AND_CHILDREN");
    }

    @Test
    void rejectsUnsupportedDataScopeCode() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.create(
                new RoleCreateRequest("部门负责人", "TEST_MANAGER", null, "ORG_CHILDREN", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ORG_AND_CHILDREN");
    }

    @Test
    void customScopeMustBeConfiguredAfterRoleCreation() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.create(
                new RoleCreateRequest("项目审计角色", "PROJECT_AUDITOR", null, "CUSTOM", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先创建角色，再通过数据权限配置入口设置CUSTOM组织范围");
    }

    @Test
    void generalRoleUpdateCannotBypassDataScopeConfiguration() {
        SysRoleEntity role = role(2L, "FINANCE_MANAGER");
        role.setVersion(0);
        when(roleMapper.selectById(2L)).thenReturn(role);

        assertThatThrownBy(() -> service.update(new RoleUpdateRequest(
                2L, 0, "财务负责人", "FINANCE_MANAGER", null,
                "ALL", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请通过数据权限配置入口修改角色数据范围");
    }

    @Test
    void superAdminRoleIsProtected() {
        SysRoleEntity role = role(1L, "SUPER_ADMIN");
        when(roleMapper.selectById(1L)).thenReturn(role);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统保护角色");
    }

    @Test
    void roleAssignedToUsersCannotBeDeleted() {
        SysRoleEntity role = role(2L, "FINANCE_MANAGER");
        when(roleMapper.selectById(2L)).thenReturn(role);
        when(managementMapper.countUsersByRole(2L)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("角色已分配给用户，禁止删除");
    }

    @Test
    void menuAssignmentAddsAncestorsAndSynchronizesPermission() {
        SysRoleEntity role = role(2L, "FINANCE_MANAGER");
        SysMenuEntity parent = menu(900L, null, "system:view");
        SysMenuEntity page = menu(930L, 900L, "system:role:view");
        SysMenuEntity button = menu(931L, 930L, "role:add");
        SysPermissionEntity p1 = permission(1009L, "system:view");
        SysPermissionEntity p2 = permission(2301L, "system:role:view");
        SysPermissionEntity p3 = permission(2302L, "role:add");
        when(roleMapper.selectById(2L)).thenReturn(role);
        when(menuMapper.selectList(any())).thenReturn(List.of(parent, page, button));
        when(permissionMapper.selectList(any())).thenReturn(List.of(p1, p2, p3));
        SysUserEntity affectedUser = new SysUserEntity();
        affectedUser.setId(88L);
        affectedUser.setTokenVersion(0);
        when(managementMapper.selectUserIdsByRole(2L)).thenReturn(List.of(88L));
        when(userMapper.selectById(88L)).thenReturn(affectedUser);
        when(userMapper.updateById(affectedUser)).thenReturn(1);

        service.assignMenus(new RoleMenuAssignRequest(2L, List.of(931L)));

        ArgumentCaptor<SysRoleMenuEntity> captor = ArgumentCaptor.forClass(SysRoleMenuEntity.class);
        verify(roleMenuMapper, atLeastOnce()).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysRoleMenuEntity::getMenuId)
                .containsExactlyInAnyOrder(900L, 930L, 931L);
        verify(rolePermissionMapper, org.mockito.Mockito.times(3)).insert(any(SysRolePermissionEntity.class));
        verify(permissionCacheService).evict(88L);
    }

    private SysRoleEntity role(Long id, String code) {
        SysRoleEntity role = new SysRoleEntity(); role.setId(id); role.setRoleCode(code); role.setRoleName(code);
        role.setDataScopeType("SELF"); role.setStatus(1); return role;
    }
    private SysMenuEntity menu(Long id, Long parentId, String permission) {
        SysMenuEntity menu = new SysMenuEntity(); menu.setId(id); menu.setParentId(parentId);
        menu.setMenuName(permission); menu.setMenuType(parentId == null ? "M" : "B");
        menu.setPermission(permission); menu.setStatus(1); return menu;
    }
    private SysPermissionEntity permission(Long id, String code) {
        SysPermissionEntity permission = new SysPermissionEntity(); permission.setId(id);
        permission.setPermissionCode(code); permission.setStatus(1); return permission;
    }
}
