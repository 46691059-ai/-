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
                entity.getId(), entity.getProjectNo(), entity.getProjectName(),
                entity.getProjectType(), entity.getProjectMode(), entity.getLeaderId(),
                entity.getDepartmentId(), entity.getStatus(), entity.getStartDate(),
                entity.getEndDate(), entity.getActualStartDate(), entity.getActualEndDate(),
                entity.getBudgetAmount(), entity.getExpectedIncome(), entity.getExpectedProfit(),
                entity.getCurrentStageCode(), entity.getRiskLevel(), entity.getProgress(),
                entity.getRemark(), entity.getCreateTime(), entity.getUpdateTime(),
                entity.getVersion());
    }

    public ProjectDtos.StageResponse stage(ProjectStageEntity entity) {
        return new ProjectDtos.StageResponse(
                entity.getId(), entity.getProjectId(), entity.getStageCode(), entity.getStageName(),
                entity.getStageOrder(), entity.getStartTime(), entity.getEndTime(),
                entity.getActualStartTime(), entity.getActualEndTime(), entity.getStatus(),
                entity.getResponsiblePerson(), entity.getApprovalStatus(),
                entity.getCompletionPercent(), entity.getRemark(), entity.getVersion());
    }

    public ProjectDtos.TaskResponse task(ProjectTaskEntity entity) {
        return new ProjectDtos.TaskResponse(
                entity.getId(), entity.getProjectId(), entity.getStageId(), entity.getParentTaskId(),
                entity.getTaskNo(), entity.getTaskName(), entity.getResponsiblePerson(),
                entity.getPlanDate(), entity.getActualDate(), entity.getStatus(),
                entity.getPriority(), entity.getProgress(), entity.getSortNo(),
                entity.getRemark(), entity.getVersion());
    }

    public ProjectDtos.MemberResponse member(ProjectMemberEntity entity) {
        return new ProjectDtos.MemberResponse(
                entity.getId(), entity.getProjectId(), entity.getEmployeeId(), entity.getRole(),
                entity.getResponsibilities(), entity.getJoinedDate(), entity.getLeftDate(),
                entity.getStatus(), entity.getRemark(), entity.getVersion());
    }
}
