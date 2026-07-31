package cn.gov.enterprise.modules.project.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.entity.ProjectStageEntity;
import cn.gov.enterprise.modules.project.entity.ProjectTaskEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectStageMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectTaskMapper;
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import cn.gov.enterprise.security.CurrentSecurityContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectTaskCommandService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private final ProjectTaskMapper taskMapper;
    private final ProjectStageMapper stageMapper;
    private final ProjectAccessPolicy accessPolicy;
    private final ProjectReferenceValidator referenceValidator;
    private final ProjectLifecycleAssembler assembler;
    private final CurrentSecurityContext securityContext;

    public ProjectTaskCommandService(
            ProjectTaskMapper taskMapper,
            ProjectStageMapper stageMapper,
            ProjectAccessPolicy accessPolicy,
            ProjectReferenceValidator referenceValidator,
            ProjectLifecycleAssembler assembler,
            CurrentSecurityContext securityContext) {
        this.taskMapper = taskMapper;
        this.stageMapper = stageMapper;
        this.accessPolicy = accessPolicy;
        this.referenceValidator = referenceValidator;
        this.assembler = assembler;
        this.securityContext = securityContext;
    }

    @Transactional
    public ProjectDtos.TaskResponse create(Long projectId, ProjectDtos.TaskRequest request) {
        validate(projectId, null, request);
        ProjectTaskEntity task = new ProjectTaskEntity();
        apply(task, projectId, request);
        taskMapper.insert(task);
        return assembler.task(task);
    }

    @Transactional
    public ProjectDtos.TaskResponse update(
            Long projectId, Long taskId, ProjectDtos.TaskRequest request) {
        if (request.version() == null) {
            throw new BusinessException("B0001", "更新任务时版本号不能为空");
        }
        validate(projectId, taskId, request);
        ProjectTaskEntity task = requireTask(projectId, taskId);
        apply(task, projectId, request);
        task.setVersion(request.version());
        if (taskMapper.updateById(task) == 0) {
            throw concurrentModification();
        }
        return assembler.task(taskMapper.selectById(taskId));
    }

    @Transactional
    public void delete(Long projectId, Long taskId) {
        accessPolicy.requireAccessible(projectId);
        ProjectTaskEntity task = requireTask(projectId, taskId);
        long childCount = taskMapper.selectCount(new LambdaQueryWrapper<ProjectTaskEntity>()
                .eq(ProjectTaskEntity::getParentTaskId, taskId));
        if (childCount > 0) {
            throw new BusinessException("B0001", "存在子任务，不能删除");
        }
        if (taskMapper.softDelete(task.getId(), securityContext.userId()) == 0) {
            throw concurrentModification();
        }
    }

    private void validate(Long projectId, Long taskId, ProjectDtos.TaskRequest request) {
        accessPolicy.requireAccessible(projectId);
        if (request.stageId() == null) {
            throw new BusinessException("B0001", "任务所属阶段不能为空");
        }
        requireStage(projectId, request.stageId());
        referenceValidator.requireActiveUser(request.assigneeUserId(), "任务负责人");
        validateDates(request.plannedStartDate(), request.plannedEndDate(), "任务计划日期");
        validateDates(request.actualStartDate(), request.actualEndDate(), "任务实际日期");
        if (request.parentTaskId() != null) {
            ProjectTaskEntity parent = requireTask(projectId, request.parentTaskId());
            ensureNoCycle(projectId, taskId, parent);
        }
        long duplicate = taskMapper.selectCount(new LambdaQueryWrapper<ProjectTaskEntity>()
                .eq(ProjectTaskEntity::getProjectId, projectId)
                .eq(ProjectTaskEntity::getTaskCode, request.taskCode().trim())
                .ne(taskId != null, ProjectTaskEntity::getId, taskId));
        if (duplicate > 0) {
            throw new BusinessException("B0001", "项目内任务编码已存在");
        }
    }

    private void apply(ProjectTaskEntity task, Long projectId, ProjectDtos.TaskRequest request) {
        task.setProjectId(projectId);
        task.setStageId(request.stageId());
        task.setParentTaskId(request.parentTaskId());
        task.setTaskCode(request.taskCode().trim());
        task.setTaskName(request.taskName().trim());
        task.setTaskType(request.taskType());
        task.setAssigneeUserId(request.assigneeUserId());
        task.setPriority(request.priority());
        task.setTaskStatus(request.taskStatus());
        task.setPlannedStartDate(request.plannedStartDate());
        task.setPlannedEndDate(request.plannedEndDate());
        task.setActualStartDate(request.actualStartDate());
        task.setActualEndDate(request.actualEndDate());
        task.setProgress(request.progress());
        if ("TODO".equals(request.taskStatus())) {
            task.setProgress(ZERO);
            task.setActualStartDate(null);
            task.setActualEndDate(null);
        } else if ("COMPLETED".equals(request.taskStatus())) {
            task.setProgress(BigDecimal.valueOf(100).setScale(2));
            if (task.getActualStartDate() == null) {
                task.setActualStartDate(LocalDate.now());
            }
            if (task.getActualEndDate() == null) {
                task.setActualEndDate(LocalDate.now());
            }
        } else if ("IN_PROGRESS".equals(request.taskStatus())
                && task.getActualStartDate() == null) {
            task.setActualStartDate(LocalDate.now());
        }
        task.setOutputDesc(request.outputDesc());
        task.setRiskDesc(request.riskDesc());
        task.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
    }

    private void ensureNoCycle(
            Long projectId, Long taskId, ProjectTaskEntity candidateParent) {
        if (taskId == null) {
            return;
        }
        ProjectTaskEntity current = candidateParent;
        for (int depth = 0; current != null && depth < 100; depth++) {
            if (taskId.equals(current.getId())) {
                throw new BusinessException("B0001", "父任务关系不能形成循环");
            }
            current = current.getParentTaskId() == null
                    ? null
                    : requireTask(projectId, current.getParentTaskId());
        }
        if (current != null) {
            throw new BusinessException("B0001", "任务层级不能超过100层");
        }
    }

    private ProjectStageEntity requireStage(Long projectId, Long stageId) {
        ProjectStageEntity stage = stageMapper.selectOne(
                new LambdaQueryWrapper<ProjectStageEntity>()
                        .eq(ProjectStageEntity::getId, stageId)
                        .eq(ProjectStageEntity::getProjectId, projectId));
        if (stage == null) {
            throw new BusinessException("B0404", "项目阶段不存在");
        }
        return stage;
    }

    private ProjectTaskEntity requireTask(Long projectId, Long taskId) {
        ProjectTaskEntity task = taskMapper.selectOne(
                new LambdaQueryWrapper<ProjectTaskEntity>()
                        .eq(ProjectTaskEntity::getId, taskId)
                        .eq(ProjectTaskEntity::getProjectId, projectId));
        if (task == null) {
            throw new BusinessException("B0404", "项目任务不存在");
        }
        return task;
    }

    private void validateDates(LocalDate start, LocalDate end, String label) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException("B0001", label + "的开始日期不能晚于结束日期");
        }
    }

    private BusinessException concurrentModification() {
        return new BusinessException("B0001", "数据已被其他操作修改，请刷新后重试");
    }
}
