package cn.gov.enterprise.modules.workflow.interfaces.rest;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowAssignmentResolverApplicationService;
import cn.gov.enterprise.modules.workflow.application.vo.AssignmentResolverDetail;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/resolvers")
public class WorkflowResolverController {
    private final WorkflowAssignmentResolverApplicationService service;

    public WorkflowResolverController(WorkflowAssignmentResolverApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('workflow:view')")
    public ApiResponse<List<AssignmentResolverDetail>> list() {
        return ApiResponse.success(service.listAvailable());
    }
}
