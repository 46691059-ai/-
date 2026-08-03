package cn.gov.enterprise.modules.system.datascope.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.system.datascope.dto.RoleDataScopeSaveRequest;
import cn.gov.enterprise.modules.system.datascope.mapper.DataScopeManagementMapper;
import cn.gov.enterprise.modules.system.datascope.service.impl.DataScopeManagementServiceImpl;
import cn.gov.enterprise.modules.system.entity.SysRoleEntity;
import cn.gov.enterprise.modules.system.entity.SysRoleOrgEntity;
import cn.gov.enterprise.modules.system.mapper.SysRoleMapper;
import cn.gov.enterprise.modules.system.mapper.SysRoleOrgMapper;
import cn.gov.enterprise.modules.system.org.service.OrgManagementService;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.PermissionCacheService;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataScopeManagementServiceImplTest {
    @Mock SysRoleMapper roleMapper;
    @Mock SysRoleOrgMapper roleOrgMapper;
    @Mock DataScopeManagementMapper managementMapper;
    @Mock OrgManagementService orgManagementService;
    @Mock CurrentSecurityContext securityContext;
    @Mock PermissionCacheService permissionCacheService;
    private DataScopeManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DataScopeManagementServiceImpl(roleMapper, roleOrgMapper,
                managementMapper, orgManagementService, securityContext, permissionCacheService);
    }

    @Test
    void queryReturnsRoleScopeAndSortedCustomOrganizations() {
        when(roleMapper.selectById(6L)).thenReturn(role(6L, "PROJECT_MANAGER", "CUSTOM"));
        when(managementMapper.selectActiveRoleOrganizationIds(6L)).thenReturn(List.of(10303L));

        var result = service.roleDataScope(6L);

        assertThat(result.dataScope()).isEqualTo("CUSTOM");
        assertThat(result.orgIds()).containsExactly(10303L);
    }

    @Test
    void projectManagerCanSaveCustomOrganizationRange() {
        SysRoleEntity role = role(6L, "PROJECT_MANAGER", "ORG_AND_CHILDREN");
        when(roleMapper.selectById(6L)).thenReturn(role);
        when(managementMapper.selectActiveRoleOrganizationIds(6L)).thenReturn(List.of());
        when(managementMapper.selectValidAdministrativeOrgIds(List.of(10303L)))
                .thenReturn(List.of(10303L));
        when(securityContext.principal()).thenReturn(principal(true, Set.of()));
        when(securityContext.username()).thenReturn("admin");
        when(roleMapper.updateById(role)).thenReturn(1);
        when(managementMapper.selectUserIdsByRole(6L)).thenReturn(List.of(90001L));

        service.saveRoleDataScope(new RoleDataScopeSaveRequest(6L, "CUSTOM", List.of(10303L)));

        assertThat(role.getDataScopeType()).isEqualTo("CUSTOM");
        verify(managementMapper).logicallyDeleteRoleOrganizations(6L, "admin");
        ArgumentCaptor<SysRoleOrgEntity> relation = ArgumentCaptor.forClass(SysRoleOrgEntity.class);
        verify(roleOrgMapper).insert(relation.capture());
        assertThat(relation.getValue().getOrgId()).isEqualTo(10303L);
        verify(permissionCacheService).evict(90001L);
    }

    @Test
    void departmentManagerCanUseOrganizationAndChildrenWithoutCustomOrganizations() {
        SysRoleEntity role = role(5L, "DEPT_MANAGER", "ORG");
        when(roleMapper.selectById(5L)).thenReturn(role);
        when(managementMapper.selectActiveRoleOrganizationIds(5L)).thenReturn(List.of(103L));
        when(securityContext.username()).thenReturn("admin");
        when(roleMapper.updateById(role)).thenReturn(1);
        when(managementMapper.selectUserIdsByRole(5L)).thenReturn(List.of(90003L));

        service.saveRoleDataScope(
                new RoleDataScopeSaveRequest(5L, "ORG_AND_CHILDREN", List.of()));

        assertThat(role.getDataScopeType()).isEqualTo("ORG_AND_CHILDREN");
        verify(managementMapper).logicallyDeleteRoleOrganizations(5L, "admin");
        verify(roleOrgMapper, never()).insert(any(SysRoleOrgEntity.class));
        verify(permissionCacheService).evict(90003L);
    }

    @Test
    void ordinaryEmployeeCanUseSelfWithoutOrganizationSelection() {
        SysRoleEntity role = role(7L, "COMMON_USER", "ORG");
        when(roleMapper.selectById(7L)).thenReturn(role);
        when(managementMapper.selectActiveRoleOrganizationIds(7L)).thenReturn(List.of());
        when(securityContext.username()).thenReturn("admin");
        when(roleMapper.updateById(role)).thenReturn(1);
        when(managementMapper.selectUserIdsByRole(7L)).thenReturn(List.of(90002L));

        service.saveRoleDataScope(new RoleDataScopeSaveRequest(7L, "SELF", List.of()));

        assertThat(role.getDataScopeType()).isEqualTo("SELF");
        verify(permissionCacheService).evict(90002L);
    }

    @Test
    void customScopeRequiresAtLeastOneOrganization() {
        when(roleMapper.selectById(6L)).thenReturn(role(6L, "PROJECT_MANAGER", "ORG"));

        assertThatThrownBy(() -> service.saveRoleDataScope(
                new RoleDataScopeSaveRequest(6L, "CUSTOM", List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessage("自定义数据范围至少需要选择一个组织");
    }

    @Test
    void customScopeRejectsDisabledOrPartyOrganization() {
        when(roleMapper.selectById(6L)).thenReturn(role(6L, "PROJECT_MANAGER", "ORG"));
        when(managementMapper.selectValidAdministrativeOrgIds(List.of(999L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.saveRoleDataScope(
                new RoleDataScopeSaveRequest(6L, "CUSTOM", List.of(999L))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("自定义范围包含不存在、停用或非行政组织");
    }

    @Test
    void scopedOperatorCannotAssignOrganizationOutsideOwnRange() {
        when(roleMapper.selectById(6L)).thenReturn(role(6L, "PROJECT_MANAGER", "ORG"));
        when(managementMapper.selectValidAdministrativeOrgIds(List.of(10303L)))
                .thenReturn(List.of(10303L));
        when(securityContext.principal()).thenReturn(principal(false, Set.of(10301L)));

        assertThatThrownBy(() -> service.saveRoleDataScope(
                new RoleDataScopeSaveRequest(6L, "CUSTOM", List.of(10303L))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能配置当前用户数据范围之外的组织");
    }

    @Test
    void scopedOperatorCannotGrantAllData() {
        when(roleMapper.selectById(5L)).thenReturn(role(5L, "DEPT_MANAGER", "ORG"));
        when(securityContext.principal()).thenReturn(principal(false, Set.of(103L)));

        assertThatThrownBy(() -> service.saveRoleDataScope(
                new RoleDataScopeSaveRequest(5L, "ALL", List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前用户无权授予全部数据范围");
    }

    @Test
    void superAdministratorScopeCannotBeChanged() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "SUPER_ADMIN", "ALL"));

        assertThatThrownBy(() -> service.saveRoleDataScope(
                new RoleDataScopeSaveRequest(1L, "SELF", List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessage("超级管理员数据范围不可修改");
    }

    private SysRoleEntity role(Long id, String code, String scope) {
        SysRoleEntity role = new SysRoleEntity();
        role.setId(id);
        role.setRoleCode(code);
        role.setDataScopeType(scope);
        role.setStatus(1);
        role.setVersion(0);
        return role;
    }

    private SecurityPrincipal principal(boolean allDataScope, Set<Long> allowedOrgIds) {
        return new SecurityPrincipal(
                1L, "admin", 100L, allowedOrgIds, allDataScope, false, 0);
    }
}
