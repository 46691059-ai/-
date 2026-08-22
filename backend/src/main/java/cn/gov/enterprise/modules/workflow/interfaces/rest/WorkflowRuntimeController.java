package cn.gov.enterprise.modules.workflow.interfaces.rest;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.workflow.application.command.StartWorkflowCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowRuntimeApplicationService;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowResolverBindingApplicationService;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowInstanceDetail;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowResolverBindingDetail;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowResolverBindingSetDetail;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowNodeResolverBindingDetail;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/instances")
public class WorkflowRuntimeController {
    private final WorkflowRuntimeApplicationService service;
    private final WorkflowResolverBindingApplicationService bindingService;

    public WorkflowRuntimeController(WorkflowRuntimeApplicationService service,
                                     WorkflowResolverBindingApplicationService bindingService) {
        this.service = service;
        this.bindingService = bindingService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('workflow:start')")
    public ApiResponse<WorkflowInstance> start(@Valid @RequestBody StartWorkflowCommand command) {
        return ApiResponse.success(service.startWorkflow(command));
    }

    @GetMapping("/{instanceId}")
    @PreAuthorize("hasAuthority('workflow:view')")
    public ApiResponse<WorkflowInstanceDetail> detail(@PathVariable Long instanceId) {
        return ApiResponse.success(service.queryInstance(instanceId));
    }

    @GetMapping("/{instanceId}/tasks")
    @PreAuthorize("hasAuthority('workflow:view')")
    public ApiResponse<List<WorkflowTask>> tasks(@PathVariable Long instanceId) {
        return ApiResponse.success(service.queryTasks(instanceId));
    }

    @GetMapping("/{instanceId}/resolver-binding")
    @PreAuthorize("hasAuthority('workflow:view')")
    public ApiResponse<WorkflowResolverBindingDetail> resolverBinding(
            @PathVariable Long instanceId) {
        return ApiResponse.success(service.queryResolverBinding(instanceId));
    }

    @GetMapping("/{instanceId}/resolver-bindings")
    @PreAuthorize("hasAuthority('workflow:view')")
    public ApiResponse<WorkflowResolverBindingSetDetail> resolverBindings(@PathVariable Long instanceId) {
        return ApiResponse.success(bindingService.querySet(instanceId));
    }

    @GetMapping("/{instanceId}/nodes/{nodeId}/resolver-binding")
    @PreAuthorize("hasAuthority('workflow:view')")
    public ApiResponse<WorkflowNodeResolverBindingDetail> nodeResolverBinding(
            @PathVariable Long instanceId, @PathVariable Long nodeId) {
        return ApiResponse.success(bindingService.queryNode(instanceId, nodeId));
    }
}
