package cn.gov.enterprise.modules.workflow.interfaces.rest;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowMultiNodeRuntimeApplicationService;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/definitions")
public class WorkflowTransitionController {
    private final WorkflowMultiNodeRuntimeApplicationService service;

    public WorkflowTransitionController(WorkflowMultiNodeRuntimeApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{definitionId}/versions/{versionId}/transitions")
    @PreAuthorize("hasAuthority('workflow:definition:view')")
    public ApiResponse<List<WorkflowTransition>> transitions(
            @PathVariable Long definitionId, @PathVariable Long versionId) {
        return ApiResponse.success(service.queryTransitions(definitionId, versionId));
    }
}
