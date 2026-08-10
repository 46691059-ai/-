package cn.gov.enterprise.modules.investment.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.investment.application.command.CreateDecisionConditionCommand;
import cn.gov.enterprise.modules.investment.application.command.ReviewDecisionConditionCommand;
import cn.gov.enterprise.modules.investment.application.command.SubmitConditionRectificationCommand;
import cn.gov.enterprise.modules.investment.application.service.InvestmentDecisionConditionService;
import cn.gov.enterprise.modules.investment.domain.model.DecisionCondition;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/investment")
public class InvestmentDecisionConditionController {
    private final InvestmentDecisionConditionService service;

    public InvestmentDecisionConditionController(InvestmentDecisionConditionService service) {
        this.service = service;
    }

    @PostMapping("/decisions/{decisionId}/conditions")
    @PreAuthorize("hasAuthority('investment:decision:condition')")
    public ApiResponse<DecisionCondition> create(@PathVariable Long decisionId,
            @Valid @RequestBody CreateDecisionConditionCommand command) {
        return ApiResponse.success(service.create(decisionId, command));
    }

    @GetMapping("/decisions/{decisionId}/conditions")
    @PreAuthorize("hasAuthority('investment:decision:view')")
    public ApiResponse<List<DecisionCondition>> list(@PathVariable Long decisionId) {
        return ApiResponse.success(service.list(decisionId));
    }

    @PostMapping("/decision-conditions/{conditionId}/start")
    @PreAuthorize("hasAuthority('investment:decision:condition')")
    public ApiResponse<DecisionCondition> start(@PathVariable Long conditionId) {
        return ApiResponse.success(service.start(conditionId));
    }

    @PostMapping("/decision-conditions/{conditionId}/submit")
    @PreAuthorize("hasAuthority('investment:decision:condition')")
    public ApiResponse<DecisionCondition> submit(@PathVariable Long conditionId,
            @Valid @RequestBody SubmitConditionRectificationCommand command) {
        return ApiResponse.success(service.submit(conditionId, command));
    }

    @PostMapping("/decision-conditions/{conditionId}/review")
    @PreAuthorize("hasAuthority('investment:decision:condition')")
    public ApiResponse<DecisionCondition> review(@PathVariable Long conditionId,
            @Valid @RequestBody ReviewDecisionConditionCommand command) {
        return ApiResponse.success(service.review(conditionId, command));
    }

    @PostMapping("/decision-conditions/{conditionId}/close")
    @PreAuthorize("hasAuthority('investment:decision:condition')")
    public ApiResponse<DecisionCondition> close(@PathVariable Long conditionId) {
        return ApiResponse.success(service.close(conditionId));
    }
}
