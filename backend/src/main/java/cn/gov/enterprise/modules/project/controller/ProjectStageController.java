package cn.gov.enterprise.modules.project.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/stages")
public class ProjectStageController {
    private final ProjectLifecycleService service;

    public ProjectStageController(ProjectLifecycleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:lifecycle:list')")
    public ApiResponse<List<ProjectDtos.StageResponse>> list(@PathVariable Long projectId) {
        return ApiResponse.success(service.stages(projectId));
    }

    @PutMapping("/{stageId}")
    @PreAuthorize("hasAuthority('project:edit')")
    public ApiResponse<ProjectDtos.StageResponse> update(
            @PathVariable Long projectId,
            @PathVariable Long stageId,
            @Valid @RequestBody ProjectDtos.StageUpdateRequest request) {
        return ApiResponse.success(service.updateStage(projectId, stageId, request));
    }
}
