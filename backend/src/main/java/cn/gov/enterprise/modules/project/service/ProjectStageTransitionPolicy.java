package cn.gov.enterprise.modules.project.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.entity.ProjectStageEntity;
import cn.gov.enterprise.modules.project.entity.ProjectTaskEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectStageMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProjectStageTransitionPolicy {
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "NOT_STARTED", Set.of("NOT_STARTED", "IN_PROGRESS", "SKIPPED"),
            "IN_PROGRESS", Set.of("IN_PROGRESS", "COMPLETED"),
            "COMPLETED", Set.of("COMPLETED"),
            "SKIPPED", Set.of("SKIPPED"));
    private static final Set<String> APPROVAL_STAGES = Set.of("INITIATION", "ACCEPTANCE");

    private final ProjectStageMapper stageMapper;
    private final ProjectTaskMapper taskMapper;

    public ProjectStageTransitionPolicy(
            ProjectStageMapper stageMapper,
            ProjectTaskMapper taskMapper) {
        this.stageMapper = stageMapper;
        this.taskMapper = taskMapper;
    }

    public void validate(ProjectStageEntity stage, ProjectDtos.StageUpdateRequest request) {
        Set<String> targets = ALLOWED_TRANSITIONS.getOrDefault(
                stage.getStageStatus(), Set.of());
        if (!targets.contains(request.stageStatus())) {
            throw new BusinessException(
                    "B0001",
                    "阶段状态不允许从%s流转到%s"
                            .formatted(stage.getStageStatus(), request.stageStatus()));
        }
        if ("IN_PROGRESS".equals(request.stageStatus())
                && !"IN_PROGRESS".equals(stage.getStageStatus())) {
            ensurePreviousStagesCompleted(stage);
        }
        if ("COMPLETED".equals(request.stageStatus())) {
            ensureTasksCompleted(stage);
            if (APPROVAL_STAGES.contains(stage.getStageCode())
                    && !"APPROVED".equals(request.approvalStatus())) {
                throw new BusinessException("B0001", "该阶段审批通过后才能完成");
            }
        }
        if ("NOT_STARTED".equals(request.stageStatus())
                && request.completionPercent().signum() != 0) {
            throw new BusinessException("B0001", "未开始阶段的完成进度必须为0");
        }
    }

    private void ensurePreviousStagesCompleted(ProjectStageEntity stage) {
        long count = stageMapper.selectCount(new LambdaQueryWrapper<ProjectStageEntity>()
                .eq(ProjectStageEntity::getProjectId, stage.getProjectId())
                .lt(ProjectStageEntity::getStageOrder, stage.getStageOrder())
                .notIn(ProjectStageEntity::getStageStatus, "COMPLETED", "SKIPPED"));
        if (count > 0) {
            throw new BusinessException("B0001", "前置阶段未完成，当前阶段不能开始");
        }
    }

    private void ensureTasksCompleted(ProjectStageEntity stage) {
        long count = taskMapper.selectCount(new LambdaQueryWrapper<ProjectTaskEntity>()
                .eq(ProjectTaskEntity::getProjectId, stage.getProjectId())
                .eq(ProjectTaskEntity::getStageId, stage.getId())
                .notIn(ProjectTaskEntity::getTaskStatus, "COMPLETED", "CANCELLED"));
        if (count > 0) {
            throw new BusinessException("B0001", "阶段仍有未完成任务，不能完成阶段");
        }
    }
}
