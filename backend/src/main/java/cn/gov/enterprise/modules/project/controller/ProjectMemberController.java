package cn.gov.enterprise.modules.project.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.project.application.service.ProjectMemberApplicationService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/projects/{projectId}/members")
public class ProjectMemberController {
    private final ProjectLifecycleService service;
    private final ProjectMemberApplicationService memberApplicationService;

    public ProjectMemberController(
            ProjectLifecycleService service,
            ProjectMemberApplicationService memberApplicationService) {
        this.service = service;
        this.memberApplicationService = memberApplicationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:lifecycle:list')")
    public ApiResponse<PageResponse<ProjectDtos.MemberResponse>> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(memberApplicationService.queryMembers(projectId, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('project:edit')")
    public ApiResponse<ProjectDtos.MemberResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDtos.MemberRequest request) {
        return ApiResponse.success(memberApplicationService.addMember(projectId, request));
    }

    @PutMapping("/{memberId}")
    @PreAuthorize("hasAuthority('project:edit')")
    public ApiResponse<ProjectDtos.MemberResponse> update(
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            @Valid @RequestBody ProjectDtos.MemberRequest request) {
        return ApiResponse.success(service.updateMember(projectId, memberId, request));
    }

    @DeleteMapping("/{memberId}")
    @PreAuthorize("hasAuthority('project:edit')")
    public ApiResponse<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long memberId) {
        memberApplicationService.deleteMember(projectId, memberId);
        return ApiResponse.success(null);
    }
}
