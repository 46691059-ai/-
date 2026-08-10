package cn.gov.enterprise.modules.investment.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.investment.infrastructure.workflow.WorkflowInboxService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/investment/workflow/v1")
public class WorkflowReliabilityController {
    private final WorkflowInboxService inboxService;

    public WorkflowReliabilityController(WorkflowInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @PostMapping("/inbox/{id}/replay")
    public ApiResponse<?> replay(@PathVariable Long id, @Valid @RequestBody ReplayRequest request) {
        return ApiResponse.success(inboxService.manualReplay(id,
                new WorkflowInboxService.ReplayCommand(
                        request.reviewerId(), request.ticketNo(), request.reason())));
    }

    public record ReplayRequest(
            @NotNull Long reviewerId,
            @NotBlank @Size(max = 100) String ticketNo,
            @NotBlank @Size(max = 500) String reason) {}
}
