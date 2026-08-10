package cn.gov.enterprise.modules.investment.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.investment.application.command.CreateDueDiligenceItemCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateDueDiligencePackageCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateDueDiligenceReportCommand;
import cn.gov.enterprise.modules.investment.application.service.InvestmentDueDiligenceApplicationService;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceItem;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligencePackage;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceReport;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/investment/due-diligence")
public class InvestmentDueDiligenceController {
    private final InvestmentDueDiligenceApplicationService service;
    public InvestmentDueDiligenceController(InvestmentDueDiligenceApplicationService service) {
        this.service = service;
    }
    @PostMapping("/{investmentId}/packages")
    @PreAuthorize("hasAuthority('investment:due_diligence:edit')")
    public ApiResponse<DueDiligencePackage> createPackage(
            @PathVariable Long investmentId,
            @RequestBody CreateDueDiligencePackageCommand command) {
        return ApiResponse.success(service.createPackage(investmentId, command));
    }
    @GetMapping("/{investmentId}/packages")
    @PreAuthorize("hasAuthority('investment:due_diligence:view')")
    public ApiResponse<List<DueDiligencePackage>> packages(@PathVariable Long investmentId) {
        return ApiResponse.success(service.queryPackages(investmentId));
    }
    @PostMapping("/packages/{packageId}/reports")
    @PreAuthorize("hasAuthority('investment:due_diligence:edit')")
    public ApiResponse<DueDiligenceReport> createReport(
            @PathVariable Long packageId,
            @RequestBody CreateDueDiligenceReportCommand command) {
        return ApiResponse.success(service.createReport(packageId, command));
    }
    @GetMapping("/packages/{packageId}/reports")
    @PreAuthorize("hasAuthority('investment:due_diligence:view')")
    public ApiResponse<List<DueDiligenceReport>> reports(@PathVariable Long packageId) {
        return ApiResponse.success(service.queryReports(packageId));
    }
    @PostMapping("/reports/{reportId}/items")
    @PreAuthorize("hasAuthority('investment:due_diligence:edit')")
    public ApiResponse<DueDiligenceItem> registerItem(
            @PathVariable Long reportId,
            @RequestBody CreateDueDiligenceItemCommand command) {
        return ApiResponse.success(service.registerItem(reportId, command));
    }
    @GetMapping("/reports/{reportId}/items")
    @PreAuthorize("hasAuthority('investment:due_diligence:view')")
    public ApiResponse<List<DueDiligenceItem>> items(@PathVariable Long reportId) {
        return ApiResponse.success(service.queryItems(reportId));
    }
}
