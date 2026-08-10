package cn.gov.enterprise.modules.investment.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.investment.application.command.CreateFeasibilityVersionCommand;
import cn.gov.enterprise.modules.investment.application.service.InvestmentFeasibilityApplicationService;
import cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentFeasibility;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/investment/feasibilities")
public class InvestmentFeasibilityController {
    private final InvestmentFeasibilityApplicationService service;
    public InvestmentFeasibilityController(InvestmentFeasibilityApplicationService service) {
        this.service = service;
    }
    @PostMapping("/{investmentId}")
    @PreAuthorize("hasAuthority('investment:feasibility:edit')")
    public ApiResponse<InvestmentFeasibility> createArchive(@PathVariable Long investmentId) {
        return ApiResponse.success(service.createArchive(investmentId));
    }
    @PostMapping("/{investmentId}/versions")
    @PreAuthorize("hasAuthority('investment:feasibility:edit')")
    public ApiResponse<FeasibilityVersion> createVersion(
            @PathVariable Long investmentId,
            @RequestBody CreateFeasibilityVersionCommand command) {
        return ApiResponse.success(service.createVersion(investmentId, command));
    }
    @GetMapping("/{investmentId}/versions")
    @PreAuthorize("hasAuthority('investment:feasibility:view')")
    public ApiResponse<List<FeasibilityVersion>> versions(@PathVariable Long investmentId) {
        return ApiResponse.success(service.queryVersions(investmentId));
    }
}
