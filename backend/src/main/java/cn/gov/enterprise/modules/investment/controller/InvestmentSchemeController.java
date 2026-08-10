package cn.gov.enterprise.modules.investment.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentSchemeVersionCommand;
import cn.gov.enterprise.modules.investment.application.service.InvestmentSchemeApplicationService;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentScheme;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentSchemeVersion;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/investment/schemes")
public class InvestmentSchemeController {
    private final InvestmentSchemeApplicationService service;

    public InvestmentSchemeController(InvestmentSchemeApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{investmentId}")
    @PreAuthorize("hasAuthority('investment:scheme:edit')")
    public ApiResponse<InvestmentScheme> create(@PathVariable Long investmentId) {
        return ApiResponse.success(service.createScheme(investmentId));
    }

    @GetMapping("/{investmentId}")
    @PreAuthorize("hasAuthority('investment:scheme:view')")
    public ApiResponse<InvestmentScheme> detail(@PathVariable Long investmentId) {
        return ApiResponse.success(service.queryScheme(investmentId));
    }

    @PostMapping("/{investmentId}/versions")
    @PreAuthorize("hasAuthority('investment:scheme:edit')")
    public ApiResponse<InvestmentSchemeVersion> createVersion(
            @PathVariable Long investmentId,
            @RequestBody CreateInvestmentSchemeVersionCommand command) {
        return ApiResponse.success(service.createVersion(investmentId, command));
    }

    @GetMapping("/{investmentId}/versions")
    @PreAuthorize("hasAuthority('investment:scheme:view')")
    public ApiResponse<List<InvestmentSchemeVersion>> versions(@PathVariable Long investmentId) {
        return ApiResponse.success(service.queryVersions(investmentId));
    }
}
