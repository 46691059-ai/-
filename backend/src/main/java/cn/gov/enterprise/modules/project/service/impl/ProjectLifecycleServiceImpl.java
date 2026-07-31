package cn.gov.enterprise.modules.project.service.impl;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.entity.ProjectMemberEntity;
import cn.gov.enterprise.modules.project.entity.ProjectStageEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectMemberMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectStageMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectTaskMapper;
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleService;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleAssembler;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleQueryService;
import cn.gov.enterprise.modules.project.service.ProjectStageTransitionPolicy;
import cn.gov.enterprise.modules.project.service.ProjectReferenceValidator;
import cn.gov.enterprise.modules.project.service.ProjectTaskCommandService;
import cn.gov.enterprise.modules.project.service.ProjectMemberCommandService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectLifecycleServiceImpl implements ProjectLifecycleService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final List<StageDefinition> DEFAULT_STAGES = List.of(
            new StageDefinition("RESERVE", "项目储备", 1),
            new StageDefinition("DEMONSTRATION", "项目论证", 2),
            new StageDefinition("INITIATION", "立项审批", 3),
            new StageDefinition("IMPLEMENTATION", "建设实施", 4),
            new StageDefinition("ACCEPTANCE", "项目验收", 5),
            new StageDefinition("OPERATION", "运营管理", 6),
            new StageDefinition("EVALUATION", "项目后评价", 7),
            new StageDefinition("ARCHIVE", "项目归档", 8));

    private final ProjectMapper projectMapper;
    private final ProjectStageMapper stageMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectAccessPolicy accessPolicy;
    private final CurrentSecurityContext securityContext;
    private final ProjectStageTransitionPolicy stageTransitionPolicy;
    private final ProjectLifecycleQueryService queryService;
    private final ProjectLifecycleAssembler assembler;
    private final ProjectReferenceValidator referenceValidator;
    private final ProjectTaskCommandService taskCommandService;
    private final ProjectMemberCommandService memberCommandService;

    public ProjectLifecycleServiceImpl(
            ProjectMapper projectMapper,
            ProjectStageMapper stageMapper,
            ProjectTaskMapper taskMapper,
            ProjectMemberMapper memberMapper,
            ProjectAccessPolicy accessPolicy,
            CurrentSecurityContext securityContext,
            ProjectStageTransitionPolicy stageTransitionPolicy,
            ProjectLifecycleQueryService queryService,
            ProjectLifecycleAssembler assembler,
            ProjectReferenceValidator referenceValidator,
            ProjectTaskCommandService taskCommandService,
            ProjectMemberCommandService memberCommandService) {
        this.projectMapper = projectMapper;
        this.stageMapper = stageMapper;
        this.taskMapper = taskMapper;
        this.memberMapper = memberMapper;
        this.accessPolicy = accessPolicy;
        this.securityContext = securityContext;
        this.stageTransitionPolicy = stageTransitionPolicy;
        this.queryService = queryService;
        this.assembler = assembler;
        this.referenceValidator = referenceValidator;
        this.taskCommandService = taskCommandService;
        this.memberCommandService = memberCommandService;
    }

    @Override
    public PageResponse<ProjectDtos.Response> page(
            long page, long size, String keyword, String status,
            String stageCode, Long departmentId) {
        return queryService.page(page, size, keyword, status, stageCode, departmentId);
    }

    @Override
    public ProjectDtos.DetailResponse detail(Long projectId) {
        return queryService.detail(projectId);
    }

    @Override
    @Transactional
    public ProjectDtos.DetailResponse create(ProjectDtos.CreateRequest request) {
        accessPolicy.requireOrgAccessible(request.departmentId());
        referenceValidator.requireActiveOrgAndLeader(
                request.departmentId(), request.leaderId());
        validateDates(request.startDate(), request.endDate(), "项目计划日期");
        if (projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getProjectNo, request.projectNo().trim())) > 0) {
            throw new BusinessException("B0001", "项目编码已存在");
        }

        ProjectEntity project = new ProjectEntity();
        project.setProjectNo(request.projectNo().trim());
        project.setProjectName(request.projectName().trim());
        project.setProjectType(request.projectType());
        project.setProjectMode(request.projectMode());
        project.setDepartmentId(request.departmentId());
        project.setLeaderId(request.leaderId());
        project.setRemark(request.remark());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setBudgetAmount(request.budgetAmount());
        project.setExpectedIncome(request.expectedIncome());
        project.setExpectedProfit(request.expectedProfit());
        project.setCurrentStageCode("RESERVE");
        project.setStatus("RESERVED");
        project.setRiskLevel(request.riskLevel());
        project.setProgress(ZERO);
        projectMapper.insert(project);

        for (StageDefinition definition : DEFAULT_STAGES) {
            ProjectStageEntity stage = new ProjectStageEntity();
            stage.setProjectId(project.getId());
            stage.setStageCode(definition.code());
            stage.setStageName(definition.name());
            stage.setStageOrder(definition.order());
            stage.setStatus(definition.order() == 1 ? "IN_PROGRESS" : "NOT_STARTED");
            stage.setResponsiblePerson(definition.order() == 1 ? request.leaderId() : null);
            stage.setStartTime(definition.order() == 1 ? request.startDate() : null);
            stage.setEndTime(definition.order() == DEFAULT_STAGES.size() ? request.endDate() : null);
            stage.setActualStartTime(definition.order() == 1 ? LocalDate.now() : null);
            stage.setApprovalStatus("NOT_SUBMITTED");
            stage.setCompletionPercent(ZERO);
            stageMapper.insert(stage);
        }

        ProjectMemberEntity manager = new ProjectMemberEntity();
        manager.setProjectId(project.getId());
        manager.setEmployeeId(request.leaderId());
        manager.setRole("MANAGER");
        manager.setResponsibilities("项目总体负责");
        manager.setJoinedDate(request.startDate() != null ? request.startDate() : LocalDate.now());
        manager.setStatus("ACTIVE");
        memberMapper.insert(manager);
        return queryService.detail(project.getId());
    }

    @Override
    @Transactional
    public ProjectDtos.Response update(Long projectId, ProjectDtos.UpdateRequest request) {
        validateDates(request.startDate(), request.endDate(), "项目计划日期");
        validateDates(request.actualStartDate(), request.actualEndDate(), "项目实际日期");
        ProjectEntity project = requireProject(projectId);
        accessPolicy.requireOrgAccessible(request.departmentId());
        referenceValidator.requireActiveOrgAndLeader(
                request.departmentId(), request.leaderId());
        Long previousLeaderId = project.getLeaderId();
        project.setProjectName(request.projectName().trim());
        project.setProjectType(request.projectType());
        project.setProjectMode(request.projectMode());
        project.setDepartmentId(request.departmentId());
        project.setLeaderId(request.leaderId());
        project.setRemark(request.remark());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setActualStartDate(request.actualStartDate());
        project.setActualEndDate(request.actualEndDate());
        project.setBudgetAmount(request.budgetAmount());
        project.setExpectedIncome(request.expectedIncome());
        project.setExpectedProfit(request.expectedProfit());
        project.setRiskLevel(request.riskLevel());
        project.setVersion(request.version());
        if (projectMapper.updateById(project) == 0) {
            throw concurrentModification();
        }
        memberCommandService.synchronizeManager(
                projectId,
                previousLeaderId,
                request.leaderId(),
                request.startDate());
        return assembler.project(projectMapper.selectById(projectId));
    }

    @Override
    @Transactional
    public void delete(Long projectId) {
        requireProject(projectId);
        String operator = securityContext.username();
        taskMapper.softDeleteByProject(projectId, operator);
        stageMapper.softDeleteByProject(projectId, operator);
        memberMapper.softDeleteByProject(projectId, operator);
        if (projectMapper.softDelete(projectId, operator) == 0) {
            throw concurrentModification();
        }
    }

    @Override
    public List<ProjectDtos.StageResponse> stages(Long projectId) {
        return queryService.stages(projectId);
    }

    @Override
    @Transactional
    public ProjectDtos.StageResponse updateStage(
            Long projectId, Long stageId, ProjectDtos.StageUpdateRequest request) {
        requireProject(projectId);
        validateDates(request.startTime(), request.endTime(), "阶段计划日期");
        validateDates(request.actualStartTime(), request.actualEndTime(), "阶段实际日期");
        ProjectStageEntity stage = requireStage(projectId, stageId);
        referenceValidator.requireActiveEmployee(request.responsiblePerson(), "阶段负责人");
        stageTransitionPolicy.validate(stage, request);
        stage.setResponsiblePerson(request.responsiblePerson());
        stage.setStartTime(request.startTime());
        stage.setEndTime(request.endTime());
        stage.setActualStartTime(request.actualStartTime());
        stage.setActualEndTime(request.actualEndTime());
        stage.setStatus(request.status());
        stage.setApprovalStatus(request.approvalStatus());
        stage.setCompletionPercent(request.completionPercent());
        stage.setRemark(request.remark());
        stage.setVersion(request.version());
        if ("IN_PROGRESS".equals(stage.getStatus()) && stage.getActualStartTime() == null) {
            stage.setActualStartTime(LocalDate.now());
        }
        if ("COMPLETED".equals(stage.getStatus())) {
            stage.setCompletionPercent(BigDecimal.valueOf(100).setScale(2));
            if (stage.getActualEndTime() == null) {
                stage.setActualEndTime(LocalDate.now());
            }
        }
        if (stageMapper.updateById(stage) == 0) {
            throw concurrentModification();
        }
        recalculateProject(projectId);
        return assembler.stage(stageMapper.selectById(stageId));
    }

    @Override
    public PageResponse<ProjectDtos.TaskResponse> tasks(
            Long projectId, Long stageId, long page, long size) {
        return queryService.tasks(projectId, stageId, page, size);
    }

    @Override
    @Transactional
    public ProjectDtos.TaskResponse createTask(Long projectId, ProjectDtos.TaskRequest request) {
        return taskCommandService.create(projectId, request);
    }

    @Override
    @Transactional
    public ProjectDtos.TaskResponse updateTask(
            Long projectId, Long taskId, ProjectDtos.TaskRequest request) {
        return taskCommandService.update(projectId, taskId, request);
    }

    @Override
    @Transactional
    public void deleteTask(Long projectId, Long taskId) {
        taskCommandService.delete(projectId, taskId);
    }

    @Override
    public PageResponse<ProjectDtos.MemberResponse> members(
            Long projectId, long page, long size) {
        return queryService.members(projectId, page, size);
    }

    @Override
    @Transactional
    public ProjectDtos.MemberResponse addMember(Long projectId, ProjectDtos.MemberRequest request) {
        return memberCommandService.add(projectId, request);
    }

    @Override
    @Transactional
    public ProjectDtos.MemberResponse updateMember(
            Long projectId, Long memberId, ProjectDtos.MemberRequest request) {
        return memberCommandService.update(projectId, memberId, request);
    }

    @Override
    @Transactional
    public void deleteMember(Long projectId, Long memberId) {
        memberCommandService.delete(projectId, memberId);
    }

    private void recalculateProject(Long projectId) {
        List<ProjectStageEntity> stages = selectStageEntities(projectId);
        BigDecimal progress = stages.stream()
                .map(ProjectStageEntity::getCompletionPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(stages.size()), 2, RoundingMode.HALF_UP);
        ProjectStageEntity current = stages.stream()
                .filter(stage -> !"COMPLETED".equals(stage.getStatus())
                        && !"SKIPPED".equals(stage.getStatus()))
                .findFirst()
                .orElse(null);
        ProjectEntity project = requireProject(projectId);
        project.setProgress(progress);
        if (current == null) {
            project.setCurrentStageCode("ARCHIVE");
            project.setStatus("COMPLETED");
            project.setActualEndDate(project.getActualEndDate() == null
                    ? LocalDate.now() : project.getActualEndDate());
        } else {
            project.setCurrentStageCode(current.getStageCode());
            project.setStatus("RESERVE".equals(current.getStageCode())
                    ? "RESERVED" : "IN_PROGRESS");
            if (project.getActualStartDate() == null && !"RESERVE".equals(current.getStageCode())) {
                project.setActualStartDate(LocalDate.now());
            }
        }
        if (projectMapper.updateById(project) == 0) {
            throw concurrentModification();
        }
    }

    private List<ProjectStageEntity> selectStageEntities(Long projectId) {
        return stageMapper.selectList(new LambdaQueryWrapper<ProjectStageEntity>()
                .eq(ProjectStageEntity::getProjectId, projectId)
                .orderByAsc(ProjectStageEntity::getStageOrder));
    }

    private ProjectEntity requireProject(Long id) {
        return accessPolicy.requireAccessible(id);
    }

    private ProjectStageEntity requireStage(Long projectId, Long stageId) {
        ProjectStageEntity entity = stageMapper.selectOne(
                new LambdaQueryWrapper<ProjectStageEntity>()
                        .eq(ProjectStageEntity::getId, stageId)
                        .eq(ProjectStageEntity::getProjectId, projectId));
        if (entity == null) {
            throw new BusinessException("B0404", "项目阶段不存在");
        }
        return entity;
    }

    private void validateDates(LocalDate start, LocalDate end, String label) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException("B0001", label + "的开始日期不能晚于结束日期");
        }
    }

    private BusinessException concurrentModification() {
        return new BusinessException("B0001", "数据已被其他操作修改，请刷新后重试");
    }

    private record StageDefinition(String code, String name, int order) {
    }
}
