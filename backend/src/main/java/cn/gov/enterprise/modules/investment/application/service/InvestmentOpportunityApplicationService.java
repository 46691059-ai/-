package cn.gov.enterprise.modules.investment.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.command.ConvertInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.command.ReviewInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentOpportunityScopedLoader;
import cn.gov.enterprise.modules.investment.application.vo.OpportunityConversionResult;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentOpportunity;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentOpportunityRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentProjectRepository;
import cn.gov.enterprise.modules.project.application.service.ProjectApplicationService;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import cn.gov.enterprise.security.CurrentSecurityContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for the complete investment-opportunity workflow. */
@Service
public class InvestmentOpportunityApplicationService {
    private final InvestmentOpportunityRepository opportunityRepository;
    private final InvestmentProjectRepository investmentProjectRepository;
    private final InvestmentIdentityGenerator identityGenerator;
    private final ProjectAccessPolicy projectAccessPolicy;
    private final CurrentSecurityContext securityContext;
    private final InvestmentOpportunityScopedLoader scopedLoader;
    private final ProjectApplicationService projectApplicationService;

    public InvestmentOpportunityApplicationService(
            InvestmentOpportunityRepository opportunityRepository,
            InvestmentProjectRepository investmentProjectRepository,
            InvestmentIdentityGenerator identityGenerator,
            ProjectAccessPolicy projectAccessPolicy,
            CurrentSecurityContext securityContext,
            InvestmentOpportunityScopedLoader scopedLoader,
            ProjectApplicationService projectApplicationService) {
        this.opportunityRepository = opportunityRepository;
        this.investmentProjectRepository = investmentProjectRepository;
        this.identityGenerator = identityGenerator;
        this.projectAccessPolicy = projectAccessPolicy;
        this.securityContext = securityContext;
        this.scopedLoader = scopedLoader;
        this.projectApplicationService = projectApplicationService;
    }

    @PreAuthorize("hasAuthority('investment:opportunity:create')")
    @Transactional
    public InvestmentOpportunity createInvestmentOpportunity(
            CreateInvestmentOpportunityCommand command) {
        if (command == null) {
            throw new BusinessException("B0610", "投资机会创建参数不能为空");
        }
        projectAccessPolicy.requireOrgAccessible(command.proposingOrgId());
        if (opportunityRepository.existsByOpportunityNo(command.opportunityNo())) {
            throw new BusinessException("B0619", "投资机会编号已存在");
        }
        InvestmentOpportunity opportunity = new InvestmentOpportunity(
                identityGenerator.nextId(), command.opportunityNo(), command.opportunityName(),
                command.source(), command.proposingOrgId(), securityContext.userId(),
                command.investmentDirection(), command.partnerName(),
                command.preliminaryReturn() == null ? BigDecimal.ZERO : command.preliminaryReturn(),
                InvestmentOpportunity.Status.REGISTERED, null, null, null, null, 0);
        opportunityRepository.save(opportunity);
        return opportunity;
    }

    @PreAuthorize("hasAuthority('investment:view')")
    @Transactional(readOnly = true)
    public InvestmentOpportunity queryInvestmentOpportunity(Long opportunityId) {
        InvestmentOpportunity opportunity = scopedLoader.load(opportunityId);
        requireAccessible(opportunity);
        return opportunity;
    }

    @PreAuthorize("hasAuthority('investment:opportunity:create')")
    @Transactional
    public InvestmentOpportunity submitOpportunity(Long opportunityId) {
        InvestmentOpportunity current = lockAccessible(opportunityId);
        return changeAndPersist(current, current::submit);
    }

    @PreAuthorize("hasAuthority('investment:opportunity:review')")
    @Transactional
    public InvestmentOpportunity reviewOpportunity(
            Long opportunityId, ReviewInvestmentOpportunityCommand command) {
        if (command == null || command.decision() == null) {
            throw new BusinessException("B0620", "审核决定不能为空");
        }
        InvestmentOpportunity current = lockAccessible(opportunityId);
        return changeAndPersist(current, () -> command.decision()
                == ReviewInvestmentOpportunityCommand.Decision.APPROVE
                ? current.approveReview(command.conclusion())
                : current.reject(command.conclusion()));
    }

    @PreAuthorize("hasAuthority('investment:opportunity:create')")
    @Transactional
    public InvestmentOpportunity resubmitOpportunity(Long opportunityId) {
        InvestmentOpportunity current = lockAccessible(opportunityId);
        return changeAndPersist(current, current::resubmit);
    }

    @PreAuthorize("hasAuthority('investment:opportunity:review')")
    @Transactional
    public InvestmentOpportunity closeOpportunity(Long opportunityId, String conclusion) {
        InvestmentOpportunity current = lockAccessible(opportunityId);
        return changeAndPersist(current, () -> current.close(conclusion));
    }

    /**
     * Converts an evaluated opportunity in one transaction. The locked read makes a repeated
     * request return the existing result; the final status/version CAS protects against races.
     */
    @PreAuthorize("hasAuthority('investment:opportunity:convert')")
    @Transactional
    public OpportunityConversionResult convertOpportunity(
            Long opportunityId, ConvertInvestmentOpportunityCommand command) {
        if (command == null) {
            throw new BusinessException("B0630", "机会转项目参数不能为空");
        }
        InvestmentOpportunity current = lockAccessible(opportunityId);
        if (current.status() == InvestmentOpportunity.Status.CONVERTED) {
            projectAccessPolicy.requireAccessible(current.convertedProjectId());
            return conversionResult(current, true);
        }
        if (current.status() != InvestmentOpportunity.Status.EVALUATING) {
            throw new BusinessException("B0630", "只有评估中的机会可以转项目");
        }
        validateConversionCommand(command);

        ProjectDtos.CreateRequest projectRequest = new ProjectDtos.CreateRequest(
                command.projectNo(), current.opportunityName(), "01", command.projectMode(),
                current.ownerId(), current.proposingOrgId(), command.startDate(), command.endDate(),
                command.investmentAmount(), current.preliminaryReturn(), command.expectedProfit(),
                command.riskLevel(), command.remark());
        ProjectDtos.DetailResponse createdProject = projectApplicationService.createProject(projectRequest);
        Long projectId = createdProject.project().id();

        if (investmentProjectRepository.existsByInvestmentNo(command.investmentNo())) {
            throw new BusinessException("B0609", "投资编号已存在");
        }
        if (investmentProjectRepository.findByProjectId(projectId).isPresent()) {
            throw new BusinessException("B0609", "新建项目已关联投资事项");
        }
        InvestmentProject investmentProject = new InvestmentProject(
                identityGenerator.nextId(), projectId, command.investmentNo(),
                current.opportunityName(), command.investmentType(), command.investmentMethod(),
                command.investmentAmount(), command.investmentRatio(), current.proposingOrgId(),
                current.ownerId(), InvestmentProject.Status.DRAFT);
        investmentProjectRepository.save(investmentProject);

        InvestmentOpportunity converted = current.convert(
                projectId, investmentProject.id(), LocalDateTime.now());
        if (!opportunityRepository.updateState(
                converted, current.status(), current.version(), securityContext.username())) {
            throw new BusinessException("B0639", "投资机会状态已变化，请刷新后重试");
        }
        return conversionResult(converted, false);
    }

    private InvestmentOpportunity lockAccessible(Long opportunityId) {
        InvestmentOpportunity opportunity = scopedLoader.lock(opportunityId);
        requireAccessible(opportunity);
        return opportunity;
    }

    private void requireAccessible(InvestmentOpportunity opportunity) {
        projectAccessPolicy.requireOrgAccessible(opportunity.proposingOrgId());
        if (opportunity.convertedProjectId() != null) {
            projectAccessPolicy.requireAccessible(opportunity.convertedProjectId());
        }
    }

    private InvestmentOpportunity persistTransition(
            InvestmentOpportunity current, InvestmentOpportunity changed) {
        if (!opportunityRepository.updateState(
                changed, current.status(), current.version(), securityContext.username())) {
            throw new BusinessException("B0629", "投资机会状态已变化，请刷新后重试");
        }
        return changed;
    }

    private InvestmentOpportunity changeAndPersist(
            InvestmentOpportunity current,
            Supplier<InvestmentOpportunity> transition) {
        try {
            return persistTransition(current, transition.get());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new BusinessException("B0620", exception.getMessage());
        }
    }

    private void validateConversionCommand(ConvertInvestmentOpportunityCommand command) {
        requireText(command.projectNo(), "项目编号");
        requireText(command.investmentNo(), "投资编号");
        requireText(command.riskLevel(), "风险等级");
        if (command.investmentType() == null || command.investmentMethod() == null) {
            throw new BusinessException("B0630", "投资类型和出资方式不能为空");
        }
        if (command.investmentMethod() == InvestmentProject.InvestmentMethod.LEGACY_UNKNOWN) {
            throw new BusinessException("B0630", "新投资事项不得使用历史未知出资方式");
        }
        if (command.investmentAmount() == null || command.investmentAmount().signum() < 0) {
            throw new BusinessException("B0630", "投资金额不能为空且不能为负数");
        }
        if (command.expectedProfit() == null) {
            throw new BusinessException("B0630", "预计利润不能为空");
        }
        if (command.startDate() != null && command.endDate() != null
                && command.startDate().isAfter(command.endDate())) {
            throw new BusinessException("B0630", "计划开始日期不能晚于结束日期");
        }
    }

    private void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("B0630", label + "不能为空");
        }
    }

    private OpportunityConversionResult conversionResult(
            InvestmentOpportunity opportunity, boolean alreadyConverted) {
        return new OpportunityConversionResult(
                opportunity.id(), opportunity.convertedProjectId(),
                opportunity.convertedInvestmentProjectId(), alreadyConverted);
    }
}
