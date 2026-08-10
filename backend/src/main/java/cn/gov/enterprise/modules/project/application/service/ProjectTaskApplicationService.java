package cn.gov.enterprise.modules.project.application.service;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import cn.gov.enterprise.modules.project.domain.model.task.ProjectTask;
import cn.gov.enterprise.modules.project.domain.repository.ProjectIdentityGenerator;
import cn.gov.enterprise.modules.project.domain.repository.ProjectTaskRepository;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.service.ProjectReferenceValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application use cases for migrated task query, create and update operations. */
@Service
public class ProjectTaskApplicationService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ProjectResourceAccessService resourceAccessService;
    private final ProjectTaskRepository repository;
    private final ProjectIdentityGenerator identityGenerator;
    private final ProjectReferenceValidator referenceValidator;

    public ProjectTaskApplicationService(
            ProjectResourceAccessService resourceAccessService,
            ProjectTaskRepository repository,
            ProjectIdentityGenerator identityGenerator,
            ProjectReferenceValidator referenceValidator) {
        this.resourceAccessService = resourceAccessService;
        this.repository = repository;
        this.identityGenerator = identityGenerator;
        this.referenceValidator = referenceValidator;
    }

    public PageResponse<ProjectDtos.TaskResponse> queryTasks(
            Long projectId, Long stageId, long page, long size) {
        ProjectAggregate project = resourceAccessService.requireAccessible(projectId);
        if (stageId != null) requireStage(project, stageId);
        long normalizedPage = Math.max(1, page);
        long normalizedSize = Math.min(100, Math.max(1, size));
        long offset = (normalizedPage - 1) * normalizedSize;
        return new PageResponse<>(
                repository.findPage(projectId, stageId, offset, normalizedSize)
                        .stream().map(this::toResponse).toList(),
                repository.count(projectId, stageId), normalizedPage, normalizedSize);
    }

    @Transactional
    public ProjectDtos.TaskResponse createTask(
            Long projectId, ProjectDtos.TaskRequest request) {
        ProjectAggregate project = resourceAccessService.requireAccessible(projectId);
        validate(project, null, request);
        ProjectTask task = buildTask(identityGenerator.nextId(), projectId, request, null, 0);
        repository.insert(task);
        return toResponse(task);
    }

    @Transactional
    public ProjectDtos.TaskResponse updateTask(
            Long projectId, Long taskId, ProjectDtos.TaskRequest request) {
        if (request.version() == null) {
            throw new BusinessException("B0001", "更新任务时版本号不能为空");
        }
        ProjectAggregate project = resourceAccessService.requireAccessible(projectId);
        ProjectTask current = requireTask(projectId, taskId);
        validate(project, taskId, request);
        ProjectTask updated = buildTask(
                taskId, projectId, request, current, request.version());
        repository.update(updated);
        return toResponse(repository.findById(projectId, taskId)
                .orElseThrow(() -> new BusinessException("B0404", "项目任务不存在")));
    }

    private void validate(
            ProjectAggregate project, Long taskId, ProjectDtos.TaskRequest request) {
        requireStage(project, request.stageId());
        referenceValidator.requireActiveEmployee(request.responsiblePerson(), "任务负责人");
        if (request.parentTaskId() != null) {
            ensureNoCycle(project.getId(), taskId, requireTask(project.getId(), request.parentTaskId()));
        }
        if (repository.existsTaskNo(
                project.getId(), request.taskNo().trim(), taskId)) {
            throw new BusinessException("B0001", "项目内任务编码已存在");
        }
    }

    private void ensureNoCycle(Long projectId, Long taskId, ProjectTask candidateParent) {
        if (taskId == null) return;
        ProjectTask current = candidateParent;
        for (int depth = 0; current != null && depth < 100; depth++) {
            if (taskId.equals(current.getId())) {
                throw new BusinessException("B0001", "父任务关系不能形成循环");
            }
            current = current.getParentTaskId() == null
                    ? null : requireTask(projectId, current.getParentTaskId());
        }
        if (current != null) throw new BusinessException("B0001", "任务层级不能超过100层");
    }

    private void requireStage(ProjectAggregate project, Long stageId) {
        if (stageId == null || project.getLifecycle().getStages().stream()
                .noneMatch(stage -> stage.getId().equals(stageId))) {
            throw new BusinessException("B0404", "项目阶段不存在");
        }
    }

    private ProjectTask requireTask(Long projectId, Long taskId) {
        return repository.findById(projectId, taskId)
                .orElseThrow(() -> new BusinessException("B0404", "项目任务不存在"));
    }

    private ProjectTask buildTask(
            Long taskId,
            Long projectId,
            ProjectDtos.TaskRequest request,
            ProjectTask current,
            int version) {
        String status = request.status();
        BigDecimal progress = request.progress();
        LocalDate actualDate = request.actualDate();
        if ("TODO".equals(status)) {
            progress = ZERO;
            actualDate = null;
        } else if ("COMPLETED".equals(status)) {
            progress = BigDecimal.valueOf(100).setScale(2);
            if (actualDate == null) actualDate = LocalDate.now();
        }
        return new ProjectTask(
                taskId, projectId, request.stageId(), request.parentTaskId(),
                request.taskNo().trim(), request.taskName().trim(),
                current == null ? null : current.getTaskContent(), request.responsiblePerson(),
                request.planDate(), current == null ? null : current.getPlanStart(),
                current == null ? null : current.getPlanEnd(), actualDate,
                current == null ? null : current.getActualStart(),
                current == null ? null : current.getActualEnd(), status, request.priority(),
                progress, request.sortNo() == null ? 0 : request.sortNo(), request.remark(), version);
    }

    private ProjectDtos.TaskResponse toResponse(ProjectTask task) {
        return new ProjectDtos.TaskResponse(
                task.getId(), task.getProjectId(), task.getStageId(), task.getParentTaskId(),
                task.getTaskNo(), task.getTaskName(), task.getTaskContent(),
                task.getResponsiblePersonId(), task.getPlanDate(), task.getPlanStart(),
                task.getPlanEnd(), task.getActualDate(), task.getActualStart(),
                task.getActualEnd(), task.getStatus(), task.getPriority(), task.getProgress(),
                task.getSortNo(), task.getRemark(), task.getAggregateVersion());
    }
}
