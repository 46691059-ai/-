package cn.gov.enterprise.modules.investment.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentDecisionCommand;
import cn.gov.enterprise.modules.investment.application.service.InvestmentDecisionApplicationService;
import cn.gov.enterprise.modules.investment.application.service.InvestmentDecisionClosureService;
import cn.gov.enterprise.modules.investment.application.workflow.WorkflowGateway;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/investment/decisions")
public class InvestmentDecisionController {
    private final InvestmentDecisionApplicationService service;
    private final InvestmentDecisionClosureService closureService;
    public InvestmentDecisionController(InvestmentDecisionApplicationService service,
            InvestmentDecisionClosureService closureService){this.service=service;this.closureService=closureService;}
    @PostMapping @PreAuthorize("hasAuthority('investment:decision:create')")
    public ApiResponse<?> create(@Valid @RequestBody CreateInvestmentDecisionCommand c){return ApiResponse.success(service.createDecision(c));}
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('investment:decision:submit')")
    public ApiResponse<?> submit(@PathVariable Long id){return ApiResponse.success(service.submitDecision(id));}
    @PostMapping("/{id}/withdraw") @PreAuthorize("hasAuthority('investment:decision:withdraw')")
    public ApiResponse<Void> withdraw(@PathVariable Long id,@RequestBody(required=false) WithdrawRequest r){service.withdrawDecision(id,r==null?null:r.reason());return ApiResponse.success(null);}
    @GetMapping("/{id}/status") @PreAuthorize("hasAuthority('investment:decision:view')")
    public ApiResponse<?> status(@PathVariable Long id){return ApiResponse.success(service.queryStatus(id));}
    @GetMapping("/{id}/workflow") @PreAuthorize("hasAuthority('investment:decision:view')")
    public ApiResponse<WorkflowGateway.WorkflowInstance> workflow(@PathVariable Long id){return ApiResponse.success(service.queryWorkflow(id));}
    @GetMapping("/{id}/tasks") @PreAuthorize("hasAuthority('investment:decision:view')")
    public ApiResponse<List<WorkflowGateway.WorkflowTask>> tasks(@PathVariable Long id){return ApiResponse.success(service.queryTasks(id));}
    @PostMapping("/{id}/archive") @PreAuthorize("hasAuthority('investment:decision:archive')")
    public ApiResponse<?> archive(@PathVariable Long id){return ApiResponse.success(closureService.archive(id));}
    public record WithdrawRequest(String reason){}
}
