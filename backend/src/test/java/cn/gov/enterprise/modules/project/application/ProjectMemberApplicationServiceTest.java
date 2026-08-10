package cn.gov.enterprise.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.project.application.service.ProjectMemberApplicationService;
import cn.gov.enterprise.modules.project.application.service.ProjectResourceAccessService;
import cn.gov.enterprise.modules.project.domain.model.member.ProjectMember;
import cn.gov.enterprise.modules.project.domain.repository.ProjectMemberRepository;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.service.ProjectReferenceValidator;
import cn.gov.enterprise.security.CurrentSecurityContext;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ProjectMemberApplicationServiceTest {
    @Mock ProjectResourceAccessService accessService;
    @Mock ProjectMemberRepository repository;
    @Mock ProjectReferenceValidator referenceValidator;
    @Mock CurrentSecurityContext securityContext;

    private ProjectMemberApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProjectMemberApplicationService(
                accessService, repository, () -> 600L, referenceValidator, securityContext);
    }

    @Test
    void shouldAddMemberThroughDomainRepository() {
        when(accessService.requireAccessible(100L))
                .thenReturn(ProjectApplicationTestFixtures.project(100L));

        var response = service.addMember(100L, request(30L, "CORE"));

        ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getEmployeeId()).isEqualTo(30L);
        assertThat(response.id()).isEqualTo(600L);
    }

    @Test
    void shouldDeleteNonLeaderMemberLogically() {
        when(accessService.requireAccessible(100L))
                .thenReturn(ProjectApplicationTestFixtures.project(100L));
        when(repository.findById(100L, 600L)).thenReturn(Optional.of(member(600L, 30L, "CORE")));
        when(securityContext.username()).thenReturn("admin");

        service.deleteMember(100L, 600L);

        verify(repository).softDelete(600L, "admin");
    }

    @Test
    void shouldNotQueryMemberRepositoryWhenProjectAccessIsDenied() {
        when(accessService.requireAccessible(100L)).thenThrow(new AccessDeniedException("denied"));

        assertThatThrownBy(() -> service.queryMembers(100L, 1, 20))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).findPage(any(), any(Long.class), any(Long.class));
    }

    private ProjectDtos.MemberRequest request(Long employeeId, String role) {
        return new ProjectDtos.MemberRequest(
                employeeId, role, "项目实施", LocalDate.of(2026, 8, 1),
                null, "ACTIVE", null, null);
    }

    private ProjectMember member(Long id, Long employeeId, String role) {
        return new ProjectMember(
                id, 100L, employeeId, role, null, LocalDate.of(2026, 8, 1),
                null, "ACTIVE", null, 0);
    }
}
