package cn.gov.enterprise.modules.project.application.service;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.application.port.ProjectDetailQueryRepository;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleInstance;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleStage;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application entry point for incrementally migrated Project use cases. */
@Service
public class ProjectApplicationService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ProjectLifecycleService legacyProjectService;
    private final ProjectRepository projectRepository;
    private final ProjectDetailQueryRepository detailQueryRepository;
    private final ProjectIdentityGenerator identityGenerator;
    private final ProjectAccessPolicy accessPolicy;
    private final ProjectReferenceValidator referenceValidator;
    private final LifecycleTemplateRepository lifecycleTemplateRepository;

    public ProjectApplicationService(
            ProjectLifecycleService legacyProjectService,
            ProjectRepository projectRepository,
            ProjectDetailQueryRepository detailQueryRepository,
            ProjectIdentityGenerator identityGenerator,
            ProjectAccessPolicy accessPolicy,
            ProjectReferenceValidator referenceValidator,
            LifecycleTemplateRepository lifecycleTemplateRepository) {
        this.legacyProjectService = legacyProjectService;
        this.projectRepository = projectRepository;
        this.detailQueryRepository = detailQueryRepository;
        this.identityGenerator = identityGenerator;
        this.accessPolicy = accessPolicy;
        this.referenceValidator = referenceValidator;
        this.lifecycleTemplateRepository = lifecycleTemplateRepository;
    }

    /** Creates a project only from one validated ACTIVE lifecycle template version. */
    @Transactional
    public ProjectDtos.DetailResponse createProject(ProjectDtos.CreateRequest request) {
        accessPolicy.requireOrgAccessible(request.departmentId());
        referenceValidator.requireActiveOrgAndLeader(
                request.departmentId(), request.leaderId());
        validateDates(request.startDate(), request.endDate());
        String projectNo = request.projectNo().trim();
        if (projectRepository.existsByProjectNo(projectNo)) {
            throw new BusinessException("B0001", "项目编码已存在");
        }
        LifecycleTemplateDefinition template = lifecycleTemplateRepository
                .findActive(request.projectType(), request.departmentId())
                .orElseThrow(() -> new BusinessException(
                        "B0500", "当前项目类型没有可用的活动生命周期模板"));

        Long projectId = identityGenerator.nextId();
        Long lifecycleInstanceId = identityGenerator.nextId();
        List<LifecycleStage> stages = template.stages().stream()
                .map(definition -> createStage(
                        projectId, request, definition,
                        definition.stageOrder() == template.stages().size()))
                .toList();
        LifecycleInstance lifecycle = LifecycleInstance.fromTemplate(
                lifecycleInstanceId, projectId, template, snapshotChecksum(stages), stages);
        ProjectAggregate project = ProjectAggregate.create(
                projectId,
                projectNo,
                request.projectName().trim(),
                request.projectType(),
                request.projectMode(),
                request.leaderId(),
                request.departmentId(),
                request.startDate(),
                request.endDate(),
                request.budgetAmount(),
                request.expectedIncome(),
                request.expectedProfit(),
                request.riskLevel(),
                request.remark(),
                lifecycle);
        projectRepository.save(project);
        ProjectAggregate saved = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("B0500", "项目保存后读取失败"));
        return detailQueryRepository.findDetail(saved);
    }

    /** Queries one aggregate after the established data-scope and access-policy check. */
    public ProjectDtos.DetailResponse queryProject(Long projectId) {
        accessPolicy.requireAccessible(projectId);
        ProjectAggregate project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("B0404", "项目不存在"));
        accessPolicy.requireAccessible(project);
        return detailQueryRepository.findDetail(project);
    }

    /** Page query remains on the compatibility service in this migration phase. */
    public PageResponse<ProjectDtos.Response> queryProjects(
            long page,
            long size,
            String keyword,
            String status,
            String stageCode,
            Long departmentId) {
        return legacyProjectService.page(page, size, keyword, status, stageCode, departmentId);
    }

    /** Compatibility facade retained while callers move to ProjectStageApplicationService. */
    public List<ProjectDtos.StageResponse> queryLifecycle(Long projectId) {
        return legacyProjectService.stages(projectId);
    }

    /** Lifecycle mutation remains on the compatibility service in this migration phase. */
    public ProjectDtos.StageResponse operateLifecycle(
            Long projectId, Long stageId, ProjectDtos.StageUpdateRequest request) {
        return legacyProjectService.updateStage(projectId, stageId, request);
    }

    private LifecycleStage createStage(
            Long projectId,
            ProjectDtos.CreateRequest request,
            LifecycleTemplateStageDefinition definition,
            boolean last) {
        boolean first = definition.autoStart();
        return new LifecycleStage(
                identityGenerator.nextId(),
                projectId,
                definition.snapshot(),
                first ? request.startDate() : null,
                last ? request.endDate() : null,
                first ? LocalDate.now() : null,
                null,
                first ? "IN_PROGRESS" : "NOT_STARTED",
                first ? request.leaderId() : null,
                "NOT_SUBMITTED",
                ZERO,
                null,
                0);
    }

    private String snapshotChecksum(List<LifecycleStage> stages) {
        String canonical = stages.stream()
                .map(stage -> String.join("|",
                        stage.getSnapshot().getStageCode(),
                        stage.getSnapshot().getStageName(),
                        Integer.toString(stage.getSnapshot().getStageOrder()),
                        stage.getSnapshot().getProgressWeight().toPlainString(),
                        Boolean.toString(stage.getSnapshot().isRequired()),
                        Boolean.toString(stage.getSnapshot().isAllowSkip()),
                        Boolean.toString(stage.getSnapshot().isApprovalRequired()),
                        String.valueOf(stage.getSnapshot().getApprovalSceneCode()),
                        String.valueOf(stage.getSnapshot().getPlannedDurationDays()),
                        stage.getSnapshot().getCompletionMode()))
                .reduce((left, right) -> left + "||" + right)
                .orElseThrow();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException("B0001", "项目计划开始日期不能晚于结束日期");
        }
    }
}
