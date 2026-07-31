package cn.gov.enterprise.modules.project.security;

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
class ProjectAccessPolicyTest {
    @Mock ProjectMapper projectMapper;
    @Mock CurrentSecurityContext securityContext;
    private ProjectAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ProjectAccessPolicy(projectMapper, securityContext);
    }

    @Test
    void rejectsProjectOutsideAllowedOrganizations() {
        ProjectEntity project = new ProjectEntity();
        project.setId(1L);
        project.setOrgId(200L);
        project.setManagerUserId(10L);
        when(projectMapper.selectById(1L)).thenReturn(project);
        when(securityContext.principal()).thenReturn(principal(Set.of(100L), false));

        assertThatThrownBy(() -> policy.requireAccessible(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void selfScopeRequiresManagerOrActiveMember() {
        ProjectEntity project = new ProjectEntity();
        project.setId(1L);
        project.setOrgId(100L);
        project.setManagerUserId(20L);
        when(projectMapper.selectById(1L)).thenReturn(project);
        when(projectMapper.countActiveMember(1L, 10L)).thenReturn(0L);
        when(securityContext.principal()).thenReturn(principal(Set.of(100L), true));

        assertThatThrownBy(() -> policy.requireAccessible(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private SecurityPrincipal principal(Set<Long> orgIds, boolean selfOnly) {
        return new SecurityPrincipal(
                10L, "tester", 100L, orgIds, false, selfOnly, 0);
    }
}
