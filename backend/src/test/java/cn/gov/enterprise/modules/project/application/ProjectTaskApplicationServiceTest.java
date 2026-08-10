package cn.gov.enterprise.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.project.application.service.ProjectResourceAccessService;
import cn.gov.enterprise.modules.project.application.service.ProjectTaskApplicationService;
import cn.gov.enterprise.modules.project.domain.model.task.ProjectTask;
import cn.gov.enterprise.modules.project.domain.repository.ProjectTaskRepository;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.service.ProjectReferenceValidator;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ProjectTaskApplicationServiceTest {
    @Mock ProjectResourceAccessService accessService;
    @Mock ProjectTaskRepository repository;
    @Mock ProjectReferenceValidator referenceValidator;

    private ProjectTaskApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProjectTaskApplicationService(
                accessService, repository, () -> 500L, referenceValidator);
    }

    @Test
    void shouldCreateTaskThroughDomainRepository() {
        when(accessService.requireAccessible(100L))
                .thenReturn(ProjectApplicationTestFixtures.project(100L));
        ProjectDtos.TaskRequest request = request(110L, "TODO", new BigDecimal("60.00"), null);

        var response = service.createTask(100L, request);

        ArgumentCaptor<ProjectTask> captor = ArgumentCaptor.forClass(ProjectTask.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualByComparingTo("0.00");
        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.stageId()).isEqualTo(110L);
    }

    @Test
    void shouldUpdateTaskAndForceCompletedProgress() {
        when(accessService.requireAccessible(100L))
                .thenReturn(ProjectApplicationTestFixtures.project(100L));
        ProjectTask current = task(500L, 0);
        ProjectTask persisted = task(500L, 2);
        when(repository.findById(100L, 500L))
                .thenReturn(java.util.Optional.of(current))
                .thenReturn(java.util.Optional.of(persisted));

        var response = service.updateTask(
                100L, 500L, request(110L, "COMPLETED", BigDecimal.TEN, 1));

        verify(repository).update(any(ProjectTask.class));
        assertThat(response.version()).isEqualTo(2);
    }

    @Test
    void shouldNotQueryTaskRepositoryWhenProjectAccessIsDenied() {
        when(accessService.requireAccessible(100L)).thenThrow(new AccessDeniedException("denied"));

        assertThatThrownBy(() -> service.queryTasks(100L, null, 1, 20))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).findPage(any(), any(), any(Long.class), any(Long.class));
    }

    private ProjectDtos.TaskRequest request(
            Long stageId, String status, BigDecimal progress, Integer version) {
        return new ProjectDtos.TaskRequest(
                stageId, null, "TASK-001", "测试任务", 30L,
                null, null, status, "HIGH", progress, 1, null, version);
    }

    private ProjectTask task(Long id, int version) {
        return new ProjectTask(
                id, 100L, 110L, null, "TASK-001", "测试任务", null, 30L,
                null, null, null, null, null, null, "COMPLETED", "HIGH",
                new BigDecimal("100.00"), 1, null, version);
    }
}
