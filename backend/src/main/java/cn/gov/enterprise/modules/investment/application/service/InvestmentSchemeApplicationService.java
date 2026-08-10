package cn.gov.enterprise.modules.investment.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentSchemeVersionCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateSchemeFundingCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligencePackage;
import cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentScheme;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentSchemeVersion;
import cn.gov.enterprise.modules.investment.domain.model.SchemeFunding;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentSchemeRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentSchemeApplicationService {
    private final InvestmentSchemeRepository repository;
    private final InvestmentIdentityGenerator identityGenerator;
    private final InvestmentLifecycleStagePolicy lifecyclePolicy;

    public InvestmentSchemeApplicationService(
            InvestmentSchemeRepository repository,
            InvestmentIdentityGenerator identityGenerator,
            InvestmentLifecycleStagePolicy lifecyclePolicy) {
        this.repository = repository;
        this.identityGenerator = identityGenerator;
        this.lifecyclePolicy = lifecyclePolicy;
    }

    @PreAuthorize("hasAuthority('investment:scheme:edit')")
    @Transactional
    public InvestmentScheme createScheme(Long investmentId) {
        lifecyclePolicy.requireStage(investmentId, InvestmentLifecycleStagePolicy.DECISION);
        if (repository.findByInvestmentId(investmentId).isPresent()) {
            throw new BusinessException("B0679", "该投资事项已存在投资方案档案");
        }
        InvestmentScheme scheme = new InvestmentScheme(identityGenerator.nextId(), investmentId,
                null, null, InvestmentScheme.Status.NOT_STARTED, 0);
        repository.saveArchive(scheme);
        return scheme;
    }

    @PreAuthorize("hasAuthority('investment:scheme:view')")
    @Transactional(readOnly = true)
    public InvestmentScheme queryScheme(Long investmentId) {
        lifecyclePolicy.requireStage(investmentId, InvestmentLifecycleStagePolicy.DECISION);
        return repository.findByInvestmentId(investmentId)
                .orElseThrow(() -> new BusinessException("B0674", "投资方案档案不存在"));
    }

    @PreAuthorize("hasAuthority('investment:scheme:edit')")
    @Transactional
    public InvestmentSchemeVersion createVersion(
            Long investmentId, CreateInvestmentSchemeVersionCommand command) {
        if (command == null) throw new BusinessException("B0670", "投资方案版本参数不能为空");
        lifecyclePolicy.requireStage(investmentId, InvestmentLifecycleStagePolicy.DECISION);
        validateArgumentationReferences(investmentId,
                command.feasibilityVersionId(), command.dueDiligencePackageId());
        InvestmentScheme header = repository.findByInvestmentId(investmentId)
                .orElseThrow(() -> new BusinessException("B0674", "投资方案档案不存在"));
        InvestmentScheme locked = repository.findByIdForUpdate(header.id())
                .orElseThrow(() -> new BusinessException("B0674", "投资方案档案不存在"));
        int versionNo = repository.nextVersionNo(locked.id());
        Long versionId = identityGenerator.nextId();
        List<SchemeFunding> funding = createFunding(
                versionId, command.totalAmount(), command.fundingSources());
        InvestmentSchemeVersion version = new InvestmentSchemeVersion(
                versionId, locked.id(), versionNo, command.schemeNo(), command.schemeName(),
                command.investmentSubjectOrgId(), command.investeeCompanyId(), command.investmentType(),
                command.totalAmount(), command.currencyCode(), command.investmentMethod(),
                command.contributionScheduleSummary(), command.preInvestmentRatio(),
                command.postInvestmentRatio(), command.shareType(), command.controlType(),
                command.governanceArrangement(), command.cooperationMode(), command.partnerArrangement(),
                command.valuationAmount(), command.valuationBaseDate(), command.incomeDistribution(),
                command.exitType(), command.exitPlan(), command.conditionsPrecedent(),
                command.feasibilityVersionId(), command.dueDiligencePackageId(), command.primaryFileId(),
                InvestmentSchemeVersion.Status.DRAFT, funding, 0);
        repository.appendVersion(version);
        if (!repository.moveCurrentVersion(locked.useVersion(version.id()), locked.version())) {
            throw new BusinessException("B0679", "投资方案档案已被并发修改，请重试");
        }
        return version;
    }

    @PreAuthorize("hasAuthority('investment:scheme:view')")
    @Transactional(readOnly = true)
    public List<InvestmentSchemeVersion> queryVersions(Long investmentId) {
        lifecyclePolicy.requireStage(investmentId, InvestmentLifecycleStagePolicy.DECISION);
        InvestmentScheme header = repository.findByInvestmentId(investmentId)
                .orElseThrow(() -> new BusinessException("B0674", "投资方案档案不存在"));
        return repository.findVersions(header.id());
    }

    private List<SchemeFunding> createFunding(
            Long versionId,
            java.math.BigDecimal totalAmount,
            List<CreateSchemeFundingCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new BusinessException("B0670", "投资方案至少需要一个资金来源");
        }
        return commands.stream().map(command -> {
            if (command == null) throw new BusinessException("B0670", "资金来源不能为空");
            return new SchemeFunding(identityGenerator.nextId(), versionId, command.fundingType(),
                    command.providerName(), command.amount(),
                    SchemeFunding.calculateRatio(command.amount(), totalAmount),
                    command.costRate(), command.availableDate(),
                    command.confirmed(), command.evidenceFileId(), 0);
        }).toList();
    }

    private void validateArgumentationReferences(
            Long investmentId, Long feasibilityVersionId, Long dueDiligencePackageId) {
        InvestmentSchemeRepository.FeasibilityReference feasibility = repository
                .findFeasibilityReference(feasibilityVersionId)
                .orElseThrow(() -> new BusinessException("B0670", "引用的可研版本不存在"));
        if (!investmentId.equals(feasibility.investmentId())) {
            throw new BusinessException("B0670", "可研版本不属于当前投资事项");
        }
        if (feasibility.status() != FeasibilityVersion.Status.FROZEN
                || feasibility.conclusion() != FeasibilityVersion.Conclusion.RECOMMENDED) {
            throw new BusinessException("B0670", "投资方案必须引用已冻结且建议实施的可研版本");
        }

        InvestmentSchemeRepository.DueDiligenceReference dueDiligence = repository
                .findDueDiligenceReference(dueDiligencePackageId)
                .orElseThrow(() -> new BusinessException("B0670", "引用的尽调包不存在"));
        if (!investmentId.equals(dueDiligence.investmentId())) {
            throw new BusinessException("B0670", "尽调包不属于当前投资事项");
        }
        if (dueDiligence.status() != DueDiligencePackage.Status.FROZEN
                || dueDiligence.conclusion() != DueDiligencePackage.Conclusion.PASS
                || dueDiligence.openBlockingCount() != 0) {
            throw new BusinessException("B0670", "投资方案必须引用已冻结、通过且无阻断问题的尽调结果");
        }
    }
}
