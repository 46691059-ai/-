package cn.gov.enterprise.modules.workflow.interfaces.rest;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowDefinitionCommand;
import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.command.ReplaceWorkflowNodesCommand;
import cn.gov.enterprise.modules.workflow.application.command.PublishWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowDefinitionApplicationService;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowDefinitionDetail;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/definitions")
public class WorkflowDefinitionController {
    private final WorkflowDefinitionApplicationService service;

    public WorkflowDefinitionController(WorkflowDefinitionApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('workflow:definition:create')")
    public ApiResponse<WorkflowDefinition> create(@Valid @RequestBody CreateWorkflowDefinitionCommand command) {
        return ApiResponse.success(service.createDefinition(command));
    }

    @GetMapping("/{definitionId}")
    @PreAuthorize("hasAuthority('workflow:definition:view')")
    public ApiResponse<WorkflowDefinitionDetail> detail(@PathVariable Long definitionId) {
        return ApiResponse.success(service.queryDefinition(definitionId));
    }

    @PostMapping("/{definitionId}/versions")
    @PreAuthorize("hasAuthority('workflow:definition:edit')")
    public ApiResponse<WorkflowVersion> createVersion(
            @PathVariable Long definitionId,
            @Valid @RequestBody CreateWorkflowVersionCommand command) {
        return ApiResponse.success(service.createVersion(definitionId, command));
    }

    @GetMapping("/{definitionId}/versions/{versionId}")
    @PreAuthorize("hasAuthority('workflow:definition:view')")
    public ApiResponse<WorkflowDefinitionDetail.VersionDetail> versionDetail(
            @PathVariable Long definitionId, @PathVariable Long versionId) {
        return ApiResponse.success(service.queryVersion(definitionId, versionId));
    }

    @PostMapping("/{definitionId}/versions/{versionId}/publish")
    @PreAuthorize("hasAuthority('workflow:definition:publish')")
    public ApiResponse<WorkflowDefinitionDetail.VersionDetail> publish(
            @PathVariable Long definitionId, @PathVariable Long versionId,
            @Valid @RequestBody PublishWorkflowVersionCommand command) {
        return ApiResponse.success(service.publishVersion(definitionId, versionId, command));
    }

    @PutMapping("/{definitionId}/versions/{versionId}/nodes")
    @PreAuthorize("hasAuthority('workflow:definition:edit')")
    public ApiResponse<List<WorkflowNode>> replaceNodes(
            @PathVariable Long definitionId,
            @PathVariable Long versionId,
            @Valid @RequestBody ReplaceWorkflowNodesCommand command) {
        return ApiResponse.success(service.replaceNodes(definitionId, versionId, command));
    }
}
