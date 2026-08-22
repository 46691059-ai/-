package cn.gov.enterprise.modules.workflow.interfaces.rest;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowMultiNodeRuntimeApplicationService;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/instances")
public class WorkflowNodeExecutionController {
    private final WorkflowMultiNodeRuntimeApplicationService service;

    public WorkflowNodeExecutionController(WorkflowMultiNodeRuntimeApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{instanceId}/node-executions")
    @PreAuthorize("hasAuthority('workflow:view')")
    public ApiResponse<List<WorkflowNodeExecution>> trajectory(@PathVariable Long instanceId) {
        return ApiResponse.success(service.queryNodeExecutions(instanceId));
    }
}
