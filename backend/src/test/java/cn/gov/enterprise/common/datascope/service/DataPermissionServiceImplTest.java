package cn.gov.enterprise.common.datascope.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.datascope.context.DataScopeType;
import cn.gov.enterprise.common.datascope.service.impl.DataPermissionServiceImpl;
import cn.gov.enterprise.modules.system.org.service.OrgDataScopeService;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityIdentityMapper;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataPermissionServiceImplTest {
    @Mock CurrentSecurityContext securityContext;
    @Mock SecurityIdentityMapper identityMapper;
    @Mock OrgDataScopeService orgDataScopeService;
    private DataPermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DataPermissionServiceImpl(securityContext, identityMapper, orgDataScopeService);
    }

    @Test
    void administratorWithAllScopeIsUnrestricted() {
        givenPrincipal(1L, "admin", 100L);
        when(identityMapper.selectRoleScopes(1L)).thenReturn(List.of(
                new SecurityIdentityMapper.RoleScopeRow(1L, "ALL")));

        var context = service.current();

        assertThat(context.dataScope()).isEqualTo(DataScopeType.ALL);
        assertThat(context.unrestricted()).isTrue();
        assertThat(context.allowedOrgIds()).isEmpty();
        verify(orgDataScopeService, never()).getChildrenOrgIds(100L);
    }

    @Test
    void departmentManagerGetsOwnOrganizationAndAllChildren() {
        givenPrincipal(90003L, "digital_manager", 103L);
        when(identityMapper.selectRoleScopes(90003L)).thenReturn(List.of(
                new SecurityIdentityMapper.RoleScopeRow(5L, "ORG_AND_CHILDREN")));
        when(orgDataScopeService.getChildrenOrgIds(103L))
                .thenReturn(List.of(103L, 10301L, 10302L, 10303L));

        var context = service.current();

        assertThat(context.dataScope()).isEqualTo(DataScopeType.ORG_AND_CHILDREN);
        assertThat(context.allowedOrgIds())
                .containsExactlyInAnyOrder(103L, 10301L, 10302L, 10303L);
        assertThat(context.selfIncluded()).isFalse();
    }

    @Test
    void ordinaryEmployeeIsRestrictedToSelf() {
        givenPrincipal(90002L, "employee_test", 10301L);
        when(identityMapper.selectRoleScopes(90002L)).thenReturn(List.of(
                new SecurityIdentityMapper.RoleScopeRow(7L, "SELF")));

        var context = service.current();

        assertThat(context.dataScope()).isEqualTo(DataScopeType.SELF);
        assertThat(context.allowedOrgIds()).isEmpty();
        assertThat(context.selfIncluded()).isTrue();
    }

    @Test
    void projectManagerCustomScopeUsesReservedRoleOrganizationRelation() {
        givenPrincipal(90001L, "project_manager", 10303L);
        when(identityMapper.selectRoleScopes(90001L)).thenReturn(List.of(
                new SecurityIdentityMapper.RoleScopeRow(6L, "CUSTOM")));
        when(identityMapper.selectCustomOrgIds(90001L)).thenReturn(List.of(10303L));

        var context = service.current();

        assertThat(context.dataScope()).isEqualTo(DataScopeType.CUSTOM);
        assertThat(context.allowedOrgIds()).containsExactly(10303L);
        assertThat(context.selfIncluded()).isFalse();
    }

    @Test
    void missingRoleScopeFailsClosedToSelf() {
        givenPrincipal(88L, "no_scope", 103L);
        when(identityMapper.selectRoleScopes(88L)).thenReturn(List.of());

        assertThat(service.current().dataScope()).isEqualTo(DataScopeType.SELF);
    }

    private void givenPrincipal(Long userId, String username, Long orgId) {
        when(securityContext.principal()).thenReturn(
                new SecurityPrincipal(userId, username, orgId, Set.of(), false, false, 0));
    }
}
