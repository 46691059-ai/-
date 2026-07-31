package cn.gov.enterprise.modules.project.service;

import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.entity.ProjectMemberEntity;
import cn.gov.enterprise.modules.project.entity.ProjectStageEntity;
import cn.gov.enterprise.modules.project.entity.ProjectTaskEntity;
import org.springframework.stereotype.Component;

@Component
public class ProjectLifecycleAssembler {
    public ProjectDtos.Response project(ProjectEntity entity) {
        return new ProjectDtos.Response(
                entity.getId(), entity.getProjectCode(), entity.getProjectName(),
                entity.getProjectType(), entity.getOrgId(), entity.getManagerUserId(),
                entity.getDescription(), entity.getPlannedStartDate(), entity.getPlannedEndDate(),
                entity.getActualStartDate(), entity.getActualEndDate(), entity.getInvestmentAmount(),
                entity.getExpectedIncome(), entity.getActualIncome(), entity.getCurrentStageCode(),
                entity.getProjectStatus(), entity.getRiskLevel(), entity.getProgress(),
                entity.getCreatedTime(), entity.getUpdatedTime(), entity.getVersion());
    }

    public ProjectDtos.StageResponse stage(ProjectStageEntity entity) {
        return new ProjectDtos.StageResponse(
                entity.getId(), entity.getProjectId(), entity.getStageCode(), entity.getStageName(),
                entity.getStageOrder(), entity.getStageStatus(), entity.getOwnerUserId(),
                entity.getPlannedStartDate(), entity.getPlannedEndDate(),
                entity.getActualStartDate(), entity.getActualEndDate(), entity.getApprovalStatus(),
                entity.getCompletionPercent(), entity.getMilestoneDesc(), entity.getRiskSummary(),
                entity.getVersion());
    }

    public ProjectDtos.TaskResponse task(ProjectTaskEntity entity) {
        return new ProjectDtos.TaskResponse(
                entity.getId(), entity.getProjectId(), entity.getStageId(), entity.getParentTaskId(),
                entity.getTaskCode(), entity.getTaskName(), entity.getTaskType(),
                entity.getAssigneeUserId(), entity.getPriority(), entity.getTaskStatus(),
                entity.getPlannedStartDate(), entity.getPlannedEndDate(),
                entity.getActualStartDate(), entity.getActualEndDate(), entity.getProgress(),
                entity.getOutputDesc(), entity.getRiskDesc(), entity.getSortNo(), entity.getVersion());
    }

    public ProjectDtos.MemberResponse member(ProjectMemberEntity entity) {
        return new ProjectDtos.MemberResponse(
                entity.getId(), entity.getProjectId(), entity.getUserId(), entity.getMemberRole(),
                entity.getResponsibilities(), entity.getJoinedDate(), entity.getLeftDate(),
                entity.getMemberStatus(), entity.getVersion());
    }
}
