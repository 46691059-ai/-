package cn.gov.enterprise.modules.project.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;

import cn.gov.enterprise.common.datascope.annotation.DataScope;
import cn.gov.enterprise.common.datascope.context.DataPermissionContext;
import cn.gov.enterprise.common.datascope.context.DataScopeRule;
import cn.gov.enterprise.common.datascope.context.DataScopeType;
import cn.gov.enterprise.common.datascope.context.SelfValueType;
import cn.gov.enterprise.common.datascope.handler.DataScopeSqlHandler;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectMemberMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectStageMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectTaskMapper;
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleAssembler;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleQueryService;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ProjectDataScopeSecurityTest {
    private static final DataScopeRule PROJECT_RULE = new DataScopeRule(
            "project_info.department_id",
            "project_info.create_by",
            SelfValueType.USERNAME);

    @Mock ProjectMapper projectMapper;
    @Mock ProjectStageMapper stageMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock ProjectAccessPolicy scopedAccessPolicy;
    @Mock CurrentSecurityContext securityContext;

    private DataScopeSqlHandler sqlHandler;
    private ProjectAccessPolicy accessPolicy;

    @BeforeEach
    void setUp() {
        sqlHandler = new DataScopeSqlHandler();
        accessPolicy = new ProjectAccessPolicy(projectMapper, securityContext);
    }

    @Test
    void administratorAllCanReadAllProjects() {
        DataPermissionContext context = context(
                1L, "admin", 1L, DataScopeType.ALL, Set.of(), false);

        assertThat(sqlHandler.condition(context, PROJECT_RULE)).isNull();
    }

    @Test
    void orgAndChildrenRestrictsDepartmentManagerToDepartmentTree() {
        DataPermissionContext context = context(
                90003L, "digital_manager", 103L,
                DataScopeType.ORG_AND_CHILDREN,
                Set.of(103L, 10301L, 10302L, 10303L), false);

        assertThat(sqlHandler.condition(context, PROJECT_RULE))
                .isEqualTo("project_info.department_id IN (103,10301,10302,10303)");
    }

    @Test
    void customRestrictsProjectManagerToConfiguredOrganizations() {
        DataPermissionContext context = context(
                90001L, "project_manager", 10303L,
                DataScopeType.CUSTOM, Set.of(10303L), false);

        assertThat(sqlHandler.condition(context, PROJECT_RULE))
                .isEqualTo("project_info.department_id IN (10303)");
    }

    @Test
    void selfRestrictsEmployeeToProjectsOwnedByUsername() {
        DataPermissionContext context = context(
                90002L, "employee_test", 10301L,
                DataScopeType.SELF, Set.of(), true);

        assertThat(sqlHandler.condition(context, PROJECT_RULE))
                .isEqualTo("project_info.create_by = 'employee_test'");
    }

    @Test
    void directProjectLookupRejectsProjectOutsideAllowedOrganizations() {
        ProjectEntity project = new ProjectEntity();
        project.setId(2001L);
        project.setDepartmentId(999L);
        when(projectMapper.selectById(2001L)).thenReturn(project);
        when(securityContext.principal()).thenReturn(new SecurityPrincipal(
                90003L, "digital_manager", 103L,
                Set.of(103L, 10301L, 10302L, 10303L), false, false, 0));

        assertThatThrownBy(() -> accessPolicy.requireAccessible(2001L))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectMapper).selectById(2001L);
    }

    @Test
    void projectReadEntrypointsDeclareDataScope() throws NoSuchMethodException {
        Method page = ProjectLifecycleQueryService.class.getMethod(
                "page", long.class, long.class, String.class, String.class,
                String.class, Long.class);
        Method lookup = ProjectAccessPolicy.class.getMethod("requireAccessible", Long.class);

        assertProjectScope(page.getAnnotation(DataScope.class));
        assertProjectScope(lookup.getAnnotation(DataScope.class));
    }

    @Test
    void detailStageTaskAndMemberQueriesAuthorizeProjectBeforeReadingChildTables() {
        ProjectEntity project = new ProjectEntity();
        project.setId(100L);
        project.setDepartmentId(103L);
        when(scopedAccessPolicy.requireAccessible(100L)).thenReturn(project);
        when(stageMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        ProjectLifecycleQueryService service = new ProjectLifecycleQueryService(
                projectMapper, stageMapper, taskMapper, memberMapper,
                scopedAccessPolicy, new ProjectLifecycleAssembler());

        service.detail(100L);
        service.stages(100L);
        service.tasks(100L, null, 1, 20);
        service.members(100L, 1, 20);

        verify(scopedAccessPolicy, times(4)).requireAccessible(100L);
        verify(stageMapper, times(2)).selectList(any());
        verify(taskMapper, times(2)).selectPage(any(Page.class), any());
        verify(memberMapper, times(2)).selectPage(any(Page.class), any());
    }

    private void assertProjectScope(DataScope scope) {
        assertThat(scope).isNotNull();
        assertThat(scope.orgField()).isEqualTo("project_info.department_id");
        assertThat(scope.userField()).isEqualTo("project_info.create_by");
        assertThat(scope.selfValueType()).isEqualTo(SelfValueType.USERNAME);
    }

    private DataPermissionContext context(
            Long userId,
            String username,
            Long orgId,
            DataScopeType type,
            Set<Long> allowedOrgIds,
            boolean selfIncluded) {
        return new DataPermissionContext(
                userId, username, orgId, Set.of(10L), type, allowedOrgIds, selfIncluded);
    }
}
