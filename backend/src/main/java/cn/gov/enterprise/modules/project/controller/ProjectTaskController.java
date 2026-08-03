package cn.gov.enterprise.modules.project.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/tasks")
public class ProjectTaskController {
    private final ProjectLifecycleService service;

    public ProjectTaskController(ProjectLifecycleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:lifecycle:list')")
    public ApiResponse<PageResponse<ProjectDtos.TaskResponse>> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long stageId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(service.tasks(projectId, stageId, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('project:edit')")
    public ApiResponse<ProjectDtos.TaskResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDtos.TaskRequest request) {
        return ApiResponse.success(service.createTask(projectId, request));
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("hasAuthority('project:edit')")
    public ApiResponse<ProjectDtos.TaskResponse> update(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody ProjectDtos.TaskRequest request) {
        return ApiResponse.success(service.updateTask(projectId, taskId, request));
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasAuthority('project:edit')")
    public ApiResponse<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long taskId) {
        service.deleteTask(projectId, taskId);
        return ApiResponse.success(null);
    }
}
