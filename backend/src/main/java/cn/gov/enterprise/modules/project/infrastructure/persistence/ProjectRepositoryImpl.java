package cn.gov.enterprise.modules.project.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleInstance;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleStage;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.StageSnapshot;
import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import cn.gov.enterprise.modules.project.domain.repository.ProjectRepository;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.entity.ProjectLifecycleInstanceEntity;
import cn.gov.enterprise.modules.project.entity.ProjectLifecycleStageSnapshotEntity;
import cn.gov.enterprise.modules.project.entity.ProjectMemberEntity;
import cn.gov.enterprise.modules.project.entity.ProjectStageEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectLifecycleInstanceMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectLifecycleStageSnapshotMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectMemberMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectStageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** MyBatis Plus adapter for the Project aggregate and its Lifecycle V2 snapshot. */
@Repository
public class ProjectRepositoryImpl implements ProjectRepository {
    private final ProjectMapper projectMapper;
    private final ProjectStageMapper stageMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectLifecycleInstanceMapper lifecycleInstanceMapper;
    private final ProjectLifecycleStageSnapshotMapper stageSnapshotMapper;

    public ProjectRepositoryImpl(
            ProjectMapper projectMapper,
            ProjectStageMapper stageMapper,
            ProjectMemberMapper memberMapper,
            ProjectLifecycleInstanceMapper lifecycleInstanceMapper,
            ProjectLifecycleStageSnapshotMapper stageSnapshotMapper) {
        this.projectMapper = projectMapper;
        this.stageMapper = stageMapper;
        this.memberMapper = memberMapper;
        this.lifecycleInstanceMapper = lifecycleInstanceMapper;
        this.stageSnapshotMapper = stageSnapshotMapper;
    }

    @Override
    public Optional<ProjectAggregate> findById(Long projectId) {
        ProjectEntity entity = projectMapper.selectById(projectId);
        if (entity == null) return Optional.empty();

        List<ProjectStageEntity> stageEntities = stageMapper.selectList(
                new LambdaQueryWrapper<ProjectStageEntity>()
                        .eq(ProjectStageEntity::getProjectId, projectId)
                        .orderByAsc(ProjectStageEntity::getStageOrder));
        if (stageEntities.isEmpty()) {
            throw new BusinessException("B0500", "项目生命周期数据不完整");
        }
        ProjectLifecycleInstanceEntity instance = lifecycleInstanceMapper.selectOne(
                new LambdaQueryWrapper<ProjectLifecycleInstanceEntity>()
                        .eq(ProjectLifecycleInstanceEntity::getProjectId, projectId));
        LifecycleInstance lifecycle = instance == null
                ? restoreUnregisteredLegacy(projectId, stageEntities)
                : restoreRegisteredLifecycle(instance, stageEntities);
        return Optional.of(toAggregate(entity, lifecycle));
    }

    @Override
    public boolean existsByProjectNo(String projectNo) {
        return projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getProjectNo, projectNo)) > 0;
    }

    @Override
    public void save(ProjectAggregate project) {
        if (!"TEMPLATE".equals(project.getLifecycle().getSourceType())) {
            throw new BusinessException("B0500", "新项目必须使用活动生命周期模板");
        }
        if (projectMapper.insert(toEntity(project)) != 1) {
            throw new BusinessException("B0500", "项目保存失败");
        }
        ProjectLifecycleInstanceEntity instance = toEntity(project.getLifecycle());
        instance.setSnapshotStatus("BUILDING");
        if (lifecycleInstanceMapper.insert(instance) != 1) {
            throw new BusinessException("B0500", "生命周期实例保存失败");
        }
        for (LifecycleStage stage : project.getLifecycle().getStages()) {
            ProjectLifecycleStageSnapshotEntity snapshot = toSnapshotEntity(
                    project.getLifecycle().getId(), stage);
            if (stageSnapshotMapper.insert(snapshot) != 1) {
                throw new BusinessException("B0500", "生命周期阶段快照保存失败");
            }
            if (stageMapper.insert(toEntity(project.getLifecycle().getId(), snapshot.getId(), stage)) != 1) {
                throw new BusinessException("B0500", "项目阶段初始化失败");
            }
        }
        ProjectMemberEntity manager = new ProjectMemberEntity();
        manager.setProjectId(project.getId());
        manager.setEmployeeId(project.getLeaderId());
        manager.setRole("MANAGER");
        manager.setResponsibilities("项目总体负责");
        manager.setJoinedDate(project.getPlannedStartDate() == null
                ? java.time.LocalDate.now() : project.getPlannedStartDate());
        manager.setStatus("ACTIVE");
        if (memberMapper.insert(manager) != 1) {
            throw new BusinessException("B0500", "项目负责人初始化失败");
        }
        int updated = lifecycleInstanceMapper.update(
                null,
                new UpdateWrapper<ProjectLifecycleInstanceEntity>()
                        .eq("id", project.getLifecycle().getId())
                        .eq("snapshot_status", "BUILDING")
                        .set("snapshot_status", "READY"));
        if (updated != 1) {
            throw new BusinessException("B0500", "生命周期快照就绪状态更新失败");
        }
    }

    private LifecycleInstance restoreRegisteredLifecycle(
            ProjectLifecycleInstanceEntity instance,
            List<ProjectStageEntity> stageEntities) {
        List<ProjectLifecycleStageSnapshotEntity> snapshots = stageSnapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectLifecycleStageSnapshotEntity>()
                        .eq(ProjectLifecycleStageSnapshotEntity::getLifecycleInstanceId,
                                instance.getId())
                        .orderByAsc(ProjectLifecycleStageSnapshotEntity::getStageOrder));
        Map<Long, ProjectLifecycleStageSnapshotEntity> snapshotsById = snapshots.stream()
                .collect(Collectors.toMap(
                        ProjectLifecycleStageSnapshotEntity::getId, Function.identity()));
        BigDecimal legacyWeight = equalWeight(stageEntities.size());
        List<LifecycleStage> stages = stageEntities.stream()
                .map(stage -> toDomain(
                        stage, snapshotsById.get(stage.getStageSnapshotId()), legacyWeight,
                        "LEGACY".equals(instance.getSourceType())))
                .toList();
        if ("TEMPLATE".equals(instance.getSourceType())
                && (snapshots.size() != stageEntities.size()
                    || !"READY".equals(instance.getSnapshotStatus()))) {
            throw new BusinessException("B0500", "模板生命周期快照数据不完整");
        }
        return LifecycleInstance.restore(
                instance.getId(), instance.getProjectId(), instance.getTemplateId(),
                instance.getTemplateVersionId(), instance.getVersionNoSnapshot(),
                instance.getTemplateChecksum(), instance.getSourceType(),
                instance.getTemplateCodeSnapshot(), instance.getTemplateNameSnapshot(),
                instance.getVersionNameSnapshot(), instance.getSnapshotChecksum(),
                instance.getProgressPolicySnapshot(), instance.getSnapshotStatus(),
                instance.getStatus(), stages);
    }

    private LifecycleInstance restoreUnregisteredLegacy(
            Long projectId, List<ProjectStageEntity> stageEntities) {
        BigDecimal weight = equalWeight(stageEntities.size());
        List<LifecycleStage> stages = stageEntities.stream()
                .map(stage -> toDomain(stage, null, weight, true))
                .toList();
        return LifecycleInstance.legacy(projectId, projectId, stages);
    }

    private LifecycleStage toDomain(
            ProjectStageEntity entity,
            ProjectLifecycleStageSnapshotEntity snapshotEntity,
            BigDecimal legacyWeight,
            boolean legacy) {
        if (!legacy && snapshotEntity == null) {
            throw new BusinessException("B0500", "项目阶段缺少生命周期快照");
        }
        StageSnapshot snapshot = snapshotEntity == null
                ? legacySnapshot(entity, legacyWeight)
                : new StageSnapshot(
                        snapshotEntity.getSourceStageTemplateId(),
                        snapshotEntity.getStageCode(), snapshotEntity.getStageName(),
                        snapshotEntity.getStageOrder(),
                        snapshotEntity.getProgressWeight() == null
                                ? legacyWeight : snapshotEntity.getProgressWeight(),
                        valueOrTrue(snapshotEntity.getRequiredFlag()),
                        valueOrFalse(snapshotEntity.getAllowSkip()),
                        valueOrFalse(snapshotEntity.getApprovalRequired()),
                        snapshotEntity.getApprovalSceneCode(),
                        snapshotEntity.getPlannedDurationDays(),
                        snapshotEntity.getCompletionMode() == null
                                ? "MANUAL" : snapshotEntity.getCompletionMode());
        return new LifecycleStage(
                entity.getId(), entity.getProjectId(), snapshot,
                entity.getStartTime(), entity.getEndTime(), entity.getActualStartTime(),
                entity.getActualEndTime(), entity.getStatus(), entity.getResponsiblePerson(),
                entity.getApprovalStatus(), valueOrZero(entity.getCompletionPercent()),
                entity.getRemark(), valueOrZero(entity.getVersion()));
    }

    private StageSnapshot legacySnapshot(ProjectStageEntity entity, BigDecimal weight) {
        return new StageSnapshot(
                null, entity.getStageCode(), entity.getStageName(), entity.getStageOrder(),
                weight, true, false, false, null);
    }

    private ProjectAggregate toAggregate(
            ProjectEntity entity, LifecycleInstance lifecycle) {
        return ProjectAggregate.restore(
                entity.getId(), entity.getProjectNo(), entity.getProjectName(),
                entity.getProjectType(), entity.getProjectMode(), entity.getSourceType(),
                entity.getCustomerId(), entity.getLeaderId(), entity.getDepartmentId(),
                entity.getStatus(), entity.getStartDate(), entity.getEndDate(),
                entity.getActualStartDate(), entity.getActualEndDate(), entity.getBudgetAmount(),
                valueOrZero(entity.getContractAmount()), valueOrZero(entity.getExpectedIncome()),
                valueOrZero(entity.getExpectedProfit()), valueOrZero(entity.getActualIncome()),
                valueOrZero(entity.getActualProfit()), entity.getCurrentStageCode(),
                entity.getRiskLevel(), valueOrZero(entity.getProgress()), entity.getDescription(),
                entity.getRemark(), entity.getCreateBy(), entity.getCreateTime(),
                entity.getUpdateTime(), lifecycle, valueOrZero(entity.getVersion()));
    }

    private ProjectEntity toEntity(ProjectAggregate project) {
        ProjectEntity entity = new ProjectEntity();
        entity.setId(project.getId());
        entity.setProjectNo(project.getProjectNo());
        entity.setProjectName(project.getProjectName());
        entity.setProjectType(project.getProjectType());
        entity.setProjectMode(project.getProjectMode());
        entity.setSourceType(project.getSourceType());
        entity.setCustomerId(project.getCustomerId());
        entity.setLeaderId(project.getLeaderId());
        entity.setDepartmentId(project.getResponsibleOrgId());
        entity.setStatus(project.getStatus());
        entity.setStartDate(project.getPlannedStartDate());
        entity.setEndDate(project.getPlannedEndDate());
        entity.setActualStartDate(project.getActualStartDate());
        entity.setActualEndDate(project.getActualEndDate());
        entity.setBudgetAmount(project.getBudgetAmount());
        entity.setContractAmount(project.getContractAmount());
        entity.setExpectedIncome(project.getExpectedIncome());
        entity.setExpectedProfit(project.getExpectedProfit());
        entity.setActualIncome(project.getActualIncome());
        entity.setActualProfit(project.getActualProfit());
        entity.setCurrentStageCode(project.getCurrentStageCode());
        entity.setRiskLevel(project.getRiskLevel());
        entity.setProgress(project.getProgress());
        entity.setDescription(project.getDescription());
        entity.setRemark(project.getRemark());
        return entity;
    }

    private ProjectLifecycleInstanceEntity toEntity(LifecycleInstance lifecycle) {
        ProjectLifecycleInstanceEntity entity = new ProjectLifecycleInstanceEntity();
        entity.setId(lifecycle.getId());
        entity.setProjectId(lifecycle.getProjectId());
        entity.setSourceType(lifecycle.getSourceType());
        entity.setTemplateId(lifecycle.getSourceTemplateId());
        entity.setTemplateVersionId(lifecycle.getSourceTemplateVersionId());
        entity.setTemplateCodeSnapshot(lifecycle.getSourceTemplateCode());
        entity.setTemplateNameSnapshot(lifecycle.getSourceTemplateName());
        entity.setVersionNoSnapshot(lifecycle.getSourceVersionNo());
        entity.setVersionNameSnapshot(lifecycle.getSourceVersionName());
        entity.setTemplateChecksum(lifecycle.getSourceChecksum());
        entity.setSnapshotChecksum(lifecycle.getSnapshotChecksum());
        entity.setProgressPolicySnapshot(lifecycle.getProgressPolicy());
        entity.setSnapshotStatus(lifecycle.getSnapshotStatus());
        entity.setStatus(lifecycle.getStatus());
        entity.setInitializedBy("APPLICATION");
        entity.setInitializedTime(LocalDateTime.now());
        return entity;
    }

    private ProjectLifecycleStageSnapshotEntity toSnapshotEntity(
            Long lifecycleInstanceId, LifecycleStage stage) {
        StageSnapshot snapshot = stage.getSnapshot();
        ProjectLifecycleStageSnapshotEntity entity = new ProjectLifecycleStageSnapshotEntity();
        entity.setId(stage.getId());
        entity.setLifecycleInstanceId(lifecycleInstanceId);
        entity.setProjectId(stage.getProjectId());
        entity.setSourceStageTemplateId(snapshot.getSourceTemplateStageId());
        entity.setStageCode(snapshot.getStageCode());
        entity.setStageName(snapshot.getStageName());
        entity.setStageOrder(snapshot.getStageOrder());
        entity.setProgressWeight(snapshot.getProgressWeight());
        entity.setWeightSource("TEMPLATE");
        entity.setRequiredFlag(booleanValue(snapshot.isRequired()));
        entity.setAllowSkip(booleanValue(snapshot.isAllowSkip()));
        entity.setApprovalRequired(booleanValue(snapshot.isApprovalRequired()));
        entity.setApprovalSceneCode(snapshot.getApprovalSceneCode());
        entity.setCompletionMode(snapshot.getCompletionMode());
        entity.setPlannedDurationDays(snapshot.getPlannedDurationDays());
        entity.setConditionSnapshotStatus("NONE");
        entity.setDefinitionChecksum(definitionChecksum(snapshot));
        return entity;
    }

    private ProjectStageEntity toEntity(
            Long lifecycleInstanceId, Long snapshotId, LifecycleStage stage) {
        StageSnapshot snapshot = stage.getSnapshot();
        ProjectStageEntity entity = new ProjectStageEntity();
        entity.setId(stage.getId());
        entity.setProjectId(stage.getProjectId());
        entity.setLifecycleInstanceId(lifecycleInstanceId);
        entity.setStageSnapshotId(snapshotId);
        entity.setStageCode(snapshot.getStageCode());
        entity.setStageName(snapshot.getStageName());
        entity.setStageOrder(snapshot.getStageOrder());
        entity.setStartTime(stage.getPlannedStartDate());
        entity.setEndTime(stage.getPlannedEndDate());
        entity.setActualStartTime(stage.getActualStartDate());
        entity.setActualEndTime(stage.getActualEndDate());
        entity.setStatus(stage.getStatus());
        entity.setResponsiblePerson(stage.getResponsiblePersonId());
        entity.setApprovalStatus(stage.getApprovalStatus());
        entity.setCompletionPercent(stage.getCompletionPercent());
        entity.setRemark(stage.getRemark());
        return entity;
    }

    private String definitionChecksum(StageSnapshot snapshot) {
        String canonical = String.join("|",
                snapshot.getStageCode(), snapshot.getStageName(),
                Integer.toString(snapshot.getStageOrder()),
                snapshot.getProgressWeight().toPlainString(),
                Boolean.toString(snapshot.isRequired()),
                Boolean.toString(snapshot.isAllowSkip()),
                Boolean.toString(snapshot.isApprovalRequired()),
                String.valueOf(snapshot.getApprovalSceneCode()),
                String.valueOf(snapshot.getPlannedDurationDays()),
                snapshot.getCompletionMode());
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static BigDecimal equalWeight(int stageCount) {
        return BigDecimal.valueOf(100)
                .divide(BigDecimal.valueOf(stageCount), 4, RoundingMode.HALF_UP);
    }

    private static int booleanValue(boolean value) { return value ? 1 : 0; }
    private static boolean valueOrFalse(Integer value) { return value != null && value == 1; }
    private static boolean valueOrTrue(Integer value) { return value == null || value == 1; }
    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }
    private static int valueOrZero(Integer value) { return value == null ? 0 : value; }
}
