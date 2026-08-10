package cn.gov.enterprise.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.project.application.port.ProjectDetailQueryRepository;
import cn.gov.enterprise.modules.project.application.service.ProjectApplicationService;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleInstance;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleStage;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.StageSnapshot;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleTemplateDefinition;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleTemplateStageDefinition;
import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import cn.gov.enterprise.modules.project.domain.repository.LifecycleTemplateRepository;
import cn.gov.enterprise.modules.project.domain.repository.ProjectIdentityGenerator;
import cn.gov.enterprise.modules.project.domain.repository.ProjectRepository;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleService;
import cn.gov.enterprise.modules.project.service.ProjectReferenceValidator;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectApplicationServiceTest {
    @Mock ProjectLifecycleService legacyProjectService;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectDetailQueryRepository detailQueryRepository;
    @Mock ProjectAccessPolicy accessPolicy;
    @Mock ProjectReferenceValidator referenceValidator;
    @Mock LifecycleTemplateRepository lifecycleTemplateRepository;

    private ProjectApplicationService applicationService;
    private final AtomicLong sequence = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        ProjectIdentityGenerator identityGenerator = sequence::incrementAndGet;
        applicationService = new ProjectApplicationService(
                legacyProjectService,
                projectRepository,
                detailQueryRepository,
                identityGenerator,
                accessPolicy,
                referenceValidator,
                lifecycleTemplateRepository);
    }

    @Test
    void shouldCreateAggregateThroughRepository() {
        ProjectDtos.CreateRequest request = new ProjectDtos.CreateRequest(
                "PRJ-001", "Repository migration", "01", null,
                20L, 10L, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "LOW", null);
        ProjectDtos.DetailResponse expected = emptyDetail();
        when(projectRepository.existsByProjectNo("PRJ-001")).thenReturn(false);
        when(lifecycleTemplateRepository.findActive("01", 10L))
                .thenReturn(Optional.of(activeTemplate()));
        when(projectRepository.findById(1001L)).thenAnswer(invocation -> {
            ProjectAggregate saved = aggregate(1001L);
            return Optional.of(saved);
        });
        when(detailQueryRepository.findDetail(any())).thenReturn(expected);

        assertThat(applicationService.createProject(request)).isSameAs(expected);

        verify(accessPolicy).requireOrgAccessible(10L);
        verify(referenceValidator).requireActiveOrgAndLeader(10L, 20L);
        ArgumentCaptor<ProjectAggregate> aggregateCaptor =
                ArgumentCaptor.forClass(ProjectAggregate.class);
        verify(projectRepository).save(aggregateCaptor.capture());
        assertThat(aggregateCaptor.getValue().getLifecycle().getStages()).hasSize(8);
        assertThat(aggregateCaptor.getValue().getLifecycle().getSourceType())
                .isEqualTo("TEMPLATE");
        assertThat(aggregateCaptor.getValue().getCurrentStageCode()).isEqualTo("OPPORTUNITY");
        assertThat(aggregateCaptor.getValue().getLifecycle().getStages().getFirst().getStatus())
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldRejectCreationWhenNoActiveTemplateExists() {
        ProjectDtos.CreateRequest request = new ProjectDtos.CreateRequest(
                "PRJ-002", "No template", "04", null,
                20L, 10L, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "LOW", null);
        when(lifecycleTemplateRepository.findActive("04", 10L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> applicationService.createProject(request))
                .isInstanceOf(cn.gov.enterprise.common.exception.BusinessException.class)
                .hasMessageContaining("活动生命周期模板");

        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldQueryAggregateAfterAccessPolicyCheck() {
        ProjectAggregate project = aggregate(100L);
        ProjectDtos.DetailResponse expected = emptyDetail();
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(detailQueryRepository.findDetail(project)).thenReturn(expected);

        assertThat(applicationService.queryProject(100L)).isSameAs(expected);

        verify(accessPolicy).requireAccessible(100L);
        verify(accessPolicy).requireAccessible(project);
        verify(projectRepository).findById(100L);
    }

    @Test
    void shouldStopBeforeRepositoryWhenScopedAccessPolicyRejectsUser() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(accessPolicy).requireAccessible(100L);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> applicationService.queryProject(100L))
                .isInstanceOf(AccessDeniedException.class);

        verify(projectRepository, never()).findById(100L);
    }

    @Test
    void shouldKeepLifecycleQueryOnCompatibilityService() {
        when(legacyProjectService.stages(100L)).thenReturn(List.of());

        assertThat(applicationService.queryLifecycle(100L)).isEmpty();
        verify(legacyProjectService).stages(100L);
    }

    private ProjectAggregate aggregate(Long projectId) {
        StageSnapshot snapshot = new StageSnapshot(
                null, "RESERVE", "Project reserve", 1,
                new BigDecimal("100.0000"), true, false, false, null);
        LifecycleStage stage = new LifecycleStage(
                projectId + 1, projectId, snapshot, null, null, null, null,
                "IN_PROGRESS", 20L, "NOT_SUBMITTED", BigDecimal.ZERO, null, 0);
        LifecycleInstance lifecycle = LifecycleInstance.legacy(
                projectId + 2, projectId, List.of(stage));
        return ProjectAggregate.create(
                projectId, "PRJ-001", "Repository migration", "04", null,
                20L, 10L, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "LOW", null, lifecycle);
    }

    private ProjectDtos.DetailResponse emptyDetail() {
        return new ProjectDtos.DetailResponse(null, List.of(), List.of(), 0, List.of(), 0);
    }

    private LifecycleTemplateDefinition activeTemplate() {
        List<LifecycleTemplateStageDefinition> stages = java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(order -> new LifecycleTemplateStageDefinition(
                        200L + order,
                        order == 1 ? "OPPORTUNITY" : "STAGE_" + order,
                        order == 1 ? "投资机会" : "阶段" + order,
                        order, new BigDecimal("12.5000"), true, false,
                        null, order == 1, false, null, "MANUAL"))
                .toList();
        return new LifecycleTemplateDefinition(
                100L, 101L, "INVESTMENT_STANDARD", "标准投资项目生命周期",
                1, "V1", "a".repeat(64), stages);
    }
}
