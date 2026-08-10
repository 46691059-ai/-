package cn.gov.enterprise.modules.investment.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.command.CreateFeasibilityVersionCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy;
import cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentFeasibility;
import cn.gov.enterprise.modules.investment.domain.repository.FeasibilityRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentFeasibilityApplicationService {
    private final FeasibilityRepository repository;
    private final InvestmentIdentityGenerator identityGenerator;
    private final InvestmentLifecycleStagePolicy lifecyclePolicy;

    public InvestmentFeasibilityApplicationService(
            FeasibilityRepository repository,
            InvestmentIdentityGenerator identityGenerator,
            InvestmentLifecycleStagePolicy lifecyclePolicy) {
        this.repository = repository;
        this.identityGenerator = identityGenerator;
        this.lifecyclePolicy = lifecyclePolicy;
    }

    @PreAuthorize("hasAuthority('investment:feasibility:edit')")
    @Transactional
    public InvestmentFeasibility createArchive(Long investmentId) {
        lifecyclePolicy.requireStage(investmentId, InvestmentLifecycleStagePolicy.FEASIBILITY);
        if (repository.findByInvestmentId(investmentId).isPresent()) {
            throw new BusinessException("B0649", "该投资事项已存在可研档案");
        }
        InvestmentFeasibility archive = new InvestmentFeasibility(
                identityGenerator.nextId(), investmentId, null, null,
                InvestmentFeasibility.Status.NOT_STARTED, 0);
        repository.saveArchive(archive);
        return archive;
    }

    @PreAuthorize("hasAuthority('investment:feasibility:edit')")
    @Transactional
    public FeasibilityVersion createVersion(
            Long investmentId, CreateFeasibilityVersionCommand command) {
        if (command == null) throw new BusinessException("B0640", "可研版本参数不能为空");
        lifecyclePolicy.requireStage(investmentId, InvestmentLifecycleStagePolicy.FEASIBILITY);
        InvestmentFeasibility header = repository.findByInvestmentId(investmentId)
                .orElseThrow(() -> new BusinessException("B0644", "可研档案不存在"));
        InvestmentFeasibility locked = repository.findByIdForUpdate(header.id())
                .orElseThrow(() -> new BusinessException("B0644", "可研档案不存在"));
        int versionNo = repository.nextVersionNo(locked.id());
        FeasibilityVersion version = new FeasibilityVersion(
                identityGenerator.nextId(), locked.id(), versionNo, command.reportNo(),
                command.reportName(), command.compilerType(), command.compilerOrgId(),
                command.compilerOrgName(), command.preparedDate(), command.baseDate(),
                command.totalInvestment(), command.annualRevenue(), command.annualCost(),
                command.annualTax(), command.annualNetProfit(), command.roi(), command.irr(),
                command.paybackPeriod(), command.riskConclusion(), command.conclusion(),
                command.conclusionSummary(), command.primaryFileId(),
                FeasibilityVersion.Status.DRAFT, 0);
        repository.appendVersion(version);
        if (!repository.moveCurrentVersion(locked.useVersion(version.id()), locked.version())) {
            throw new BusinessException("B0649", "可研档案已被并发修改，请重试");
        }
        return version;
    }

    @PreAuthorize("hasAuthority('investment:feasibility:view')")
    @Transactional(readOnly = true)
    public List<FeasibilityVersion> queryVersions(Long investmentId) {
        lifecyclePolicy.requireStage(investmentId, InvestmentLifecycleStagePolicy.FEASIBILITY);
        InvestmentFeasibility header = repository.findByInvestmentId(investmentId)
                .orElseThrow(() -> new BusinessException("B0644", "可研档案不存在"));
        return repository.findVersions(header.id());
    }
}
