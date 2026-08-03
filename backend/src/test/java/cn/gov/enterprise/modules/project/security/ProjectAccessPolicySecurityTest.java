package cn.gov.enterprise.modules.project.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectMapper;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ProjectAccessPolicySecurityTest {
    @Mock ProjectMapper projectMapper;
    @Mock CurrentSecurityContext securityContext;

    private ProjectAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ProjectAccessPolicy(projectMapper, securityContext);
    }

    @Test
    void administratorCanAccessEveryProject() {
        ProjectEntity project = project(1001L, 999L, "another_user");
        when(projectMapper.selectById(1001L)).thenReturn(project);
        when(securityContext.principal()).thenReturn(principal(
                1L, "admin", 1L, Set.of(), true, false));

        assertThat(policy.requireAccessible(1001L)).isSameAs(project);
    }

    @Test
    void projectManagerCanOnlyAccessCustomAuthorizedOrganization() {
        ProjectEntity allowed = project(2001L, 10303L, "other_creator");
        ProjectEntity denied = project(2002L, 10302L, "other_creator");
        SecurityPrincipal manager = principal(
                90001L, "project_manager", 10303L, Set.of(10303L), false, false);
        when(securityContext.principal()).thenReturn(manager);
        when(projectMapper.selectById(2001L)).thenReturn(allowed);
        when(projectMapper.selectById(2002L)).thenReturn(denied);

        assertThat(policy.requireAccessible(2001L)).isSameAs(allowed);
        assertThatThrownBy(() -> policy.requireAccessible(2002L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ordinaryEmployeeCannotAccessProjectCreatedByAnotherUser() {
        ProjectEntity project = project(3001L, 10301L, "another_user");
        when(projectMapper.selectById(3001L)).thenReturn(project);
        when(securityContext.principal()).thenReturn(principal(
                90002L, "employee_test", 10301L, Set.of(10301L), false, true));

        assertThatThrownBy(() -> policy.requireAccessible(3001L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void selfEmployeeCanAccessOwnCreatedProject() {
        ProjectEntity project = project(3002L, 10301L, "employee_test");
        when(projectMapper.selectById(3002L)).thenReturn(project);
        when(securityContext.principal()).thenReturn(principal(
                90002L, "employee_test", 10301L, Set.of(10301L), false, true));

        assertThat(policy.requireAccessible(3002L)).isSameAs(project);
    }

    @Test
    void crossOrganizationUserIsDenied() {
        ProjectEntity project = project(4001L, 200L, "digital_manager");
        when(projectMapper.selectById(4001L)).thenReturn(project);
        when(securityContext.principal()).thenReturn(principal(
                90003L, "digital_manager", 103L,
                Set.of(103L, 10301L, 10302L, 10303L), false, false));

        assertThatThrownBy(() -> policy.requireAccessible(4001L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private ProjectEntity project(Long id, Long departmentId, String createBy) {
        ProjectEntity project = new ProjectEntity();
        project.setId(id);
        project.setDepartmentId(departmentId);
        project.setCreateBy(createBy);
        return project;
    }

    private SecurityPrincipal principal(
            Long userId,
            String username,
            Long orgId,
            Set<Long> orgIds,
            boolean all,
            boolean selfOnly) {
        return new SecurityPrincipal(userId, username, orgId, orgIds, all, selfOnly, 0);
    }
}
