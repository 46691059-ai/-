package cn.gov.enterprise.modules.project.service;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import java.util.List;

public interface ProjectLifecycleService {
    PageResponse<ProjectDtos.Response> page(
            long page, long size, String keyword, String status,
            String stageCode, Long departmentId);

    ProjectDtos.DetailResponse detail(Long projectId);

    ProjectDtos.DetailResponse create(ProjectDtos.CreateRequest request);

    ProjectDtos.Response update(Long projectId, ProjectDtos.UpdateRequest request);

    void delete(Long projectId);

    List<ProjectDtos.StageResponse> stages(Long projectId);

    ProjectDtos.StageResponse updateStage(
            Long projectId, Long stageId, ProjectDtos.StageUpdateRequest request);

    PageResponse<ProjectDtos.TaskResponse> tasks(
            Long projectId, Long stageId, long page, long size);

    ProjectDtos.TaskResponse createTask(Long projectId, ProjectDtos.TaskRequest request);

    ProjectDtos.TaskResponse updateTask(Long projectId, Long taskId, ProjectDtos.TaskRequest request);

    void deleteTask(Long projectId, Long taskId);

    PageResponse<ProjectDtos.MemberResponse> members(Long projectId, long page, long size);

    ProjectDtos.MemberResponse addMember(Long projectId, ProjectDtos.MemberRequest request);

    ProjectDtos.MemberResponse updateMember(
            Long projectId, Long memberId, ProjectDtos.MemberRequest request);

    void deleteMember(Long projectId, Long memberId);
}
