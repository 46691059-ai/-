package cn.gov.enterprise.modules.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleInstance;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleStage;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.StageSnapshot;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleTemplateDefinition;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleTemplateStageDefinition;
import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.entity.ProjectMemberEntity;
import cn.gov.enterprise.modules.project.entity.ProjectLifecycleInstanceEntity;
import cn.gov.enterprise.modules.project.entity.ProjectLifecycleStageSnapshotEntity;
import cn.gov.enterprise.modules.project.entity.ProjectStageEntity;
import cn.gov.enterprise.modules.project.infrastructure.persistence.ProjectRepositoryImpl;
import cn.gov.enterprise.modules.project.mapper.ProjectMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectMemberMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectStageMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectLifecycleInstanceMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectLifecycleStageSnapshotMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectRepositoryImplTest {
    @Mock ProjectMapper projectMapper;
    @Mock ProjectStageMapper stageMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock ProjectLifecycleInstanceMapper lifecycleInstanceMapper;
    @Mock ProjectLifecycleStageSnapshotMapper stageSnapshotMapper;

    private ProjectRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new ProjectRepositoryImpl(
                projectMapper, stageMapper, memberMapper,
                lifecycleInstanceMapper, stageSnapshotMapper);
    }

    @Test
    void shouldPersistRootStagesAndManagerAsOneAggregate() {
        ProjectAggregate project = aggregate(100L);
        when(projectMapper.insert(any(ProjectEntity.class))).thenReturn(1);
        when(stageMapper.insert(any(ProjectStageEntity.class))).thenReturn(1);
        when(memberMapper.insert(any(ProjectMemberEntity.class))).thenReturn(1);
        when(lifecycleInstanceMapper.insert(any(ProjectLifecycleInstanceEntity.class))).thenReturn(1);
        when(stageSnapshotMapper.insert(any(ProjectLifecycleStageSnapshotEntity.class))).thenReturn(1);
        when(lifecycleInstanceMapper.update(any(), any())).thenReturn(1);

        repository.save(project);

        verify(projectMapper).insert(any(ProjectEntity.class));
        verify(stageMapper, times(2)).insert(any(ProjectStageEntity.class));
        verify(stageSnapshotMapper, times(2))
                .insert(any(ProjectLifecycleStageSnapshotEntity.class));
        verify(lifecycleInstanceMapper).insert(any(ProjectLifecycleInstanceEntity.class));
        verify(memberMapper).insert(any(ProjectMemberEntity.class));
    }

    @Test
    void shouldRebuildAggregateFromExistingMappers() {
        ProjectEntity project = projectEntity(100L);
        when(projectMapper.selectById(100L)).thenReturn(project);
        when(stageMapper.selectList(any())).thenReturn(List.of(
                stageEntity(101L, 100L, "RESERVE", 1),
                stageEntity(102L, 100L, "ARCHIVE", 2)));
        ProjectLifecycleInstanceEntity lifecycle = new ProjectLifecycleInstanceEntity();
        lifecycle.setId(100L);
        lifecycle.setProjectId(100L);
        lifecycle.setSourceType("LEGACY");
        lifecycle.setProgressPolicySnapshot("LEGACY_EQUAL");
        lifecycle.setSnapshotStatus("READY");
        lifecycle.setStatus("ACTIVE");
        when(lifecycleInstanceMapper.selectOne(any())).thenReturn(lifecycle);
        when(stageSnapshotMapper.selectList(any())).thenReturn(List.of());

        ProjectAggregate restored = repository.findById(100L).orElseThrow();

        assertThat(restored.getProjectNo()).isEqualTo("PRJ-001");
        assertThat(restored.getLifecycle().getStages()).hasSize(2);
        assertThat(restored.getLifecycle().getStages().getFirst().getStatus())
                .isEqualTo("IN_PROGRESS");
    }

    private ProjectAggregate aggregate(Long projectId) {
        LifecycleTemplateDefinition template = new LifecycleTemplateDefinition(
                500L, 501L, "TEST_TEMPLATE", "Test template", 1, "V1",
                "a".repeat(64), List.of(
                        templateStage(601L, "RESERVE", 1, true),
                        templateStage(602L, "ARCHIVE", 2, false)));
        List<LifecycleStage> stages = List.of(
                stage(projectId + 1, projectId, template.stages().get(0), "IN_PROGRESS"),
                stage(projectId + 2, projectId, template.stages().get(1), "NOT_STARTED"));
        LifecycleInstance lifecycle = LifecycleInstance.fromTemplate(
                projectId + 3, projectId, template, "b".repeat(64), stages);
        return ProjectAggregate.create(
                projectId, "PRJ-001", "Repository test", "04", null,
                20L, 10L, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "LOW", null, lifecycle);
    }

    private LifecycleStage stage(
            Long id, Long projectId, LifecycleTemplateStageDefinition definition, String status) {
        return new LifecycleStage(
                id, projectId, definition.snapshot(), null, null, null, null, status,
                definition.stageOrder() == 1 ? 20L : null,
                "NOT_SUBMITTED", BigDecimal.ZERO, null, 0);
    }

    private LifecycleTemplateStageDefinition templateStage(
            Long id, String code, int order, boolean autoStart) {
        return new LifecycleTemplateStageDefinition(
                id, code, code, order, new BigDecimal("50.0000"), true,
                false, null, autoStart, false, null, "MANUAL");
    }

    private ProjectEntity projectEntity(Long id) {
        ProjectEntity entity = new ProjectEntity();
        entity.setId(id);
        entity.setProjectNo("PRJ-001");
        entity.setProjectName("Repository test");
        entity.setProjectType("04");
        entity.setLeaderId(20L);
        entity.setDepartmentId(10L);
        entity.setStatus("RESERVED");
        entity.setBudgetAmount(BigDecimal.ZERO);
        entity.setContractAmount(BigDecimal.ZERO);
        entity.setExpectedIncome(BigDecimal.ZERO);
        entity.setExpectedProfit(BigDecimal.ZERO);
        entity.setActualIncome(BigDecimal.ZERO);
        entity.setActualProfit(BigDecimal.ZERO);
        entity.setCurrentStageCode("RESERVE");
        entity.setRiskLevel("LOW");
        entity.setProgress(BigDecimal.ZERO);
        entity.setVersion(0);
        return entity;
    }

    private ProjectStageEntity stageEntity(
            Long id, Long projectId, String code, int order) {
        ProjectStageEntity entity = new ProjectStageEntity();
        entity.setId(id);
        entity.setProjectId(projectId);
        entity.setStageCode(code);
        entity.setStageName(code);
        entity.setStageOrder(order);
        entity.setStatus(order == 1 ? "IN_PROGRESS" : "NOT_STARTED");
        entity.setApprovalStatus("NOT_SUBMITTED");
        entity.setCompletionPercent(BigDecimal.ZERO);
        entity.setVersion(0);
        return entity;
    }
}
