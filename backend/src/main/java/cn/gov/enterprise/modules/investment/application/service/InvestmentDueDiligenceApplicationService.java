package cn.gov.enterprise.modules.investment.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.command.CreateDueDiligenceItemCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateDueDiligencePackageCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateDueDiligenceReportCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceItem;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligencePackage;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceReport;
import cn.gov.enterprise.modules.investment.domain.repository.DueDiligenceRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentDueDiligenceApplicationService {
    private final DueDiligenceRepository repository;
    private final InvestmentIdentityGenerator identityGenerator;
    private final InvestmentLifecycleStagePolicy lifecyclePolicy;

    public InvestmentDueDiligenceApplicationService(
            DueDiligenceRepository repository,
            InvestmentIdentityGenerator identityGenerator,
            InvestmentLifecycleStagePolicy lifecyclePolicy) {
        this.repository = repository;
        this.identityGenerator = identityGenerator;
        this.lifecyclePolicy = lifecyclePolicy;
    }

    @PreAuthorize("hasAuthority('investment:due_diligence:edit')")
    @Transactional
    public DueDiligencePackage createPackage(
            Long investmentId, CreateDueDiligencePackageCommand command) {
        if (command == null) throw new BusinessException("B0650", "尽调包参数不能为空");
        lifecyclePolicy.requireStage(investmentId, InvestmentLifecycleStagePolicy.DUE_DILIGENCE);
        DueDiligencePackage value = new DueDiligencePackage(
                identityGenerator.nextId(), investmentId,
                repository.nextPackageVersion(investmentId), command.ruleVersion(),
                command.requiredTypes(), DueDiligencePackage.Conclusion.PENDING,
                0, DueDiligencePackage.Status.PLANNED, 0);
        repository.savePackage(value);
        return value;
    }

    @PreAuthorize("hasAuthority('investment:due_diligence:view')")
    @Transactional(readOnly = true)
    public List<DueDiligencePackage> queryPackages(Long investmentId) {
        lifecyclePolicy.requireStage(investmentId, InvestmentLifecycleStagePolicy.DUE_DILIGENCE);
        return repository.findPackages(investmentId);
    }

    @PreAuthorize("hasAuthority('investment:due_diligence:edit')")
    @Transactional
    public DueDiligenceReport createReport(
            Long packageId, CreateDueDiligenceReportCommand command) {
        if (command == null || command.type() == null) {
            throw new BusinessException("B0650", "尽调报告类型不能为空");
        }
        DueDiligencePackage pkg = requirePackage(packageId);
        if (!pkg.requiredTypes().contains(command.type())) {
            throw new BusinessException("B0650", "该尽调类型不在尽调包范围内");
        }
        DueDiligenceReport report = new DueDiligenceReport(
                identityGenerator.nextId(), packageId, pkg.investmentProjectId(), command.type(),
                repository.nextReportVersion(packageId, command.type()), command.reportNo(),
                command.reportName(), command.entrustedOrg(), command.leadPersonId(),
                command.startDate(), command.endDate(), command.baseDate(), command.conclusion(),
                command.conclusionSummary(), 0, 0, command.primaryFileId(),
                DueDiligenceReport.Status.DRAFT, 0);
        repository.appendReport(report);
        return report;
    }

    @PreAuthorize("hasAuthority('investment:due_diligence:view')")
    @Transactional(readOnly = true)
    public List<DueDiligenceReport> queryReports(Long packageId) {
        requirePackage(packageId);
        return repository.findReports(packageId);
    }

    @PreAuthorize("hasAuthority('investment:due_diligence:edit')")
    @Transactional
    public DueDiligenceItem registerItem(
            Long reportId, CreateDueDiligenceItemCommand command) {
        if (command == null) throw new BusinessException("B0650", "尽调问题参数不能为空");
        DueDiligenceReport report = requireReport(reportId);
        if (repository.existsItemNo(reportId, command.itemNo())) {
            throw new BusinessException("B0659", "尽调问题编号已存在");
        }
        DueDiligenceItem item = new DueDiligenceItem(
                identityGenerator.nextId(), reportId, report.investmentProjectId(),
                command.itemNo(), command.category(), command.severity(), command.blocking(),
                command.problemDescription(), command.impactDescription(),
                command.rectificationMeasure(), command.responsibleOrgId(),
                command.responsiblePersonId(), command.deadline(), DueDiligenceItem.Status.OPEN, 0);
        repository.saveItem(item);
        return item;
    }

    @PreAuthorize("hasAuthority('investment:due_diligence:view')")
    @Transactional(readOnly = true)
    public List<DueDiligenceItem> queryItems(Long reportId) {
        requireReport(reportId);
        return repository.findItems(reportId);
    }

    private DueDiligencePackage requirePackage(Long packageId) {
        Long investmentId = repository.findInvestmentIdByPackageId(packageId)
                .orElseThrow(() -> new BusinessException("B0654", "尽调包不存在"));
        lifecyclePolicy.requireStage(
                investmentId, InvestmentLifecycleStagePolicy.DUE_DILIGENCE);
        DueDiligencePackage pkg = repository.findPackage(packageId)
                .orElseThrow(() -> new BusinessException("B0654", "尽调包不存在"));
        if (!investmentId.equals(pkg.investmentProjectId())) {
            throw new BusinessException("B0650", "尽调包归属在查询期间发生变化");
        }
        return pkg;
    }

    private DueDiligenceReport requireReport(Long reportId) {
        Long investmentId = repository.findInvestmentIdByReportId(reportId)
                .orElseThrow(() -> new BusinessException("B0654", "尽调报告不存在"));
        lifecyclePolicy.requireStage(
                investmentId, InvestmentLifecycleStagePolicy.DUE_DILIGENCE);
        DueDiligenceReport report = repository.findReport(reportId)
                .orElseThrow(() -> new BusinessException("B0654", "尽调报告不存在"));
        if (!investmentId.equals(report.investmentProjectId())) {
            throw new BusinessException("B0650", "尽调报告归属在查询期间发生变化");
        }
        return report;
    }
}
