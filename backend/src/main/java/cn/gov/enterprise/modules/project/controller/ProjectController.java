package cn.gov.enterprise.modules.project.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/projects")
public class ProjectController {
    private final ProjectLifecycleService service;

    public ProjectController(ProjectLifecycleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:lifecycle:list')")
    public ApiResponse<PageResponse<ProjectDtos.Response>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String stageCode,
            @RequestParam(required = false) Long orgId) {
        return ApiResponse.success(service.page(page, size, keyword, status, stageCode, orgId));
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("hasAuthority('project:lifecycle:list')")
    public ApiResponse<ProjectDtos.DetailResponse> detail(@PathVariable Long projectId) {
        return ApiResponse.success(service.detail(projectId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('project:lifecycle:create')")
    public ApiResponse<ProjectDtos.DetailResponse> create(
            @Valid @RequestBody ProjectDtos.CreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{projectId}")
    @PreAuthorize("hasAuthority('project:lifecycle:update')")
    public ApiResponse<ProjectDtos.Response> update(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDtos.UpdateRequest request) {
        return ApiResponse.success(service.update(projectId, request));
    }

    @DeleteMapping("/{projectId}")
    @PreAuthorize("hasAuthority('project:lifecycle:delete')")
    public ApiResponse<Void> delete(@PathVariable Long projectId) {
        service.delete(projectId);
        return ApiResponse.success(null);
    }
}
