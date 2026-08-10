package cn.gov.enterprise.modules.investment.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.investment.application.command.ConvertInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.command.OpportunityConclusionCommand;
import cn.gov.enterprise.modules.investment.application.command.ReviewInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.service.InvestmentOpportunityApplicationService;
import cn.gov.enterprise.modules.investment.application.vo.OpportunityConversionResult;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentOpportunity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin REST adapter. Every status mutation delegates to the Application Service. */
@RestController
@RequestMapping("/investment/opportunities")
public class InvestmentOpportunityController {
    private final InvestmentOpportunityApplicationService applicationService;

    public InvestmentOpportunityController(
            InvestmentOpportunityApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('investment:opportunity:create')")
    public ApiResponse<InvestmentOpportunity> create(
            @RequestBody CreateInvestmentOpportunityCommand command) {
        return ApiResponse.success(applicationService.createInvestmentOpportunity(command));
    }

    @GetMapping("/{opportunityId}")
    @PreAuthorize("hasAuthority('investment:view')")
    public ApiResponse<InvestmentOpportunity> detail(@PathVariable Long opportunityId) {
        return ApiResponse.success(applicationService.queryInvestmentOpportunity(opportunityId));
    }

    @PutMapping("/{opportunityId}/submit")
    @PreAuthorize("hasAuthority('investment:opportunity:create')")
    public ApiResponse<InvestmentOpportunity> submit(@PathVariable Long opportunityId) {
        return ApiResponse.success(applicationService.submitOpportunity(opportunityId));
    }

    @PutMapping("/{opportunityId}/review")
    @PreAuthorize("hasAuthority('investment:opportunity:review')")
    public ApiResponse<InvestmentOpportunity> review(
            @PathVariable Long opportunityId,
            @RequestBody ReviewInvestmentOpportunityCommand command) {
        return ApiResponse.success(applicationService.reviewOpportunity(opportunityId, command));
    }

    @PutMapping("/{opportunityId}/resubmit")
    @PreAuthorize("hasAuthority('investment:opportunity:create')")
    public ApiResponse<InvestmentOpportunity> resubmit(@PathVariable Long opportunityId) {
        return ApiResponse.success(applicationService.resubmitOpportunity(opportunityId));
    }

    @PutMapping("/{opportunityId}/close")
    @PreAuthorize("hasAuthority('investment:opportunity:review')")
    public ApiResponse<InvestmentOpportunity> close(
            @PathVariable Long opportunityId,
            @RequestBody OpportunityConclusionCommand command) {
        return ApiResponse.success(
                applicationService.closeOpportunity(opportunityId, command.conclusion()));
    }

    @PostMapping("/{opportunityId}/convert")
    @PreAuthorize("hasAuthority('investment:opportunity:convert')")
    public ApiResponse<OpportunityConversionResult> convert(
            @PathVariable Long opportunityId,
            @RequestBody ConvertInvestmentOpportunityCommand command) {
        return ApiResponse.success(applicationService.convertOpportunity(opportunityId, command));
    }
}
