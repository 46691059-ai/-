package cn.gov.enterprise.modules.workflow.interfaces.rest;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.workflow.application.command.ProcessWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.application.command.ClaimWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowTaskActionApplicationService;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowLinearExecutionApplicationService;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowTaskAssignmentApplicationService;
import cn.gov.enterprise.modules.workflow.application.service.CandidatePoolApplicationService;
import cn.gov.enterprise.modules.workflow.application.service.TaskClaimApplicationService;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowLinearCompletionResult;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowAssignmentResolutionDetail;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowTaskAssignmentDetail;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowCandidatePoolDetail;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTaskAction;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaimResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/tasks")
public class WorkflowTaskController {
    private final WorkflowTaskActionApplicationService service;
    private final WorkflowLinearExecutionApplicationService linearService;
    private final WorkflowTaskAssignmentApplicationService assignmentService;
    private final CandidatePoolApplicationService candidatePoolService;
    private final TaskClaimApplicationService claimService;

    public WorkflowTaskController(
            WorkflowTaskActionApplicationService service,
            WorkflowLinearExecutionApplicationService linearService,
            WorkflowTaskAssignmentApplicationService assignmentService,
            CandidatePoolApplicationService candidatePoolService,
            TaskClaimApplicationService claimService) {
        this.service = service;
        this.linearService = linearService;
        this.assignmentService = assignmentService;
        this.candidatePoolService = candidatePoolService;
        this.claimService = claimService;
    }

    @PostMapping("/{taskId}/claim")
    @PreAuthorize("hasAuthority('workflow:approve')")
    public ApiResponse<TaskClaimResult> claim(
            @PathVariable Long taskId,
            @Valid @RequestBody ClaimWorkflowTaskCommand command) {
        return ApiResponse.success(claimService.claim(taskId, command));
    }

    @GetMapping("/{taskId}/candidates")
    @PreAuthorize("hasAuthority('workflow:view')")
    public ApiResponse<WorkflowCandidatePoolDetail> candidates(@PathVariable Long taskId) {
        return ApiResponse.success(candidatePoolService.query(taskId));
    }

    @GetMapping("/{taskId}/assignment")
    @PreAuthorize("hasAuthority('workflow:view')")
    public ApiResponse<WorkflowTaskAssignmentDetail> assignment(@PathVariable Long taskId) {
        return ApiResponse.success(assignmentService.query(taskId));
    }

    @GetMapping("/{taskId}/assignment/resolution")
    @PreAuthorize("hasAuthority('workflow:view')")
    public ApiResponse<WorkflowAssignmentResolutionDetail> assignmentResolution(
            @PathVariable Long taskId) {
        return ApiResponse.success(assignmentService.queryResolution(taskId));
    }

    @PostMapping("/{taskId}/complete")
    @PreAuthorize("hasAuthority('workflow:approve')")
    public ApiResponse<WorkflowLinearCompletionResult> complete(
            @PathVariable Long taskId,
            @Valid @RequestBody ProcessWorkflowTaskCommand command) {
        return ApiResponse.success(linearService.complete(taskId, command));
    }

    @PostMapping("/{taskId}/approve")
    @PreAuthorize("hasAuthority('workflow:approve')")
    public ApiResponse<WorkflowTaskAction> approve(
            @PathVariable Long taskId,
            @Valid @RequestBody ProcessWorkflowTaskCommand command) {
        return ApiResponse.success(service.approve(taskId, command));
    }

    @PostMapping("/{taskId}/reject")
    @PreAuthorize("hasAuthority('workflow:approve')")
    public ApiResponse<WorkflowTaskAction> reject(
            @PathVariable Long taskId,
            @Valid @RequestBody ProcessWorkflowTaskCommand command) {
        return ApiResponse.success(service.reject(taskId, command));
    }

    @PostMapping("/{taskId}/withdraw")
    @PreAuthorize("hasAuthority('workflow:withdraw')")
    public ApiResponse<WorkflowTaskAction> withdraw(
            @PathVariable Long taskId,
            @Valid @RequestBody ProcessWorkflowTaskCommand command) {
        return ApiResponse.success(service.withdraw(taskId, command));
    }
}
