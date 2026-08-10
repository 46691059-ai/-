package cn.gov.enterprise.modules.project.application.service;

import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleStage;
import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** V2-ready lifecycle stage read use case. */
@Service
public class ProjectStageApplicationService {
    private final ProjectResourceAccessService resourceAccessService;

    public ProjectStageApplicationService(ProjectResourceAccessService resourceAccessService) {
        this.resourceAccessService = resourceAccessService;
    }

    public List<ProjectDtos.StageResponse> queryStages(Long projectId) {
        ProjectAggregate project = resourceAccessService.requireAccessible(projectId);
        return project.getLifecycle().getStages().stream()
                .sorted(Comparator.comparingInt(stage -> stage.getSnapshot().getStageOrder()))
                .map(this::toResponse)
                .toList();
    }

    private ProjectDtos.StageResponse toResponse(LifecycleStage stage) {
        return new ProjectDtos.StageResponse(
                stage.getId(), stage.getProjectId(), stage.getSnapshot().getStageCode(),
                stage.getSnapshot().getStageName(), stage.getSnapshot().getStageOrder(),
                stage.getPlannedStartDate(), stage.getPlannedEndDate(), stage.getActualStartDate(),
                stage.getActualEndDate(), stage.getStatus(), stage.getResponsiblePersonId(),
                stage.getApprovalStatus(), stage.getCompletionPercent(), stage.getRemark(),
                stage.getVersion());
    }
}
