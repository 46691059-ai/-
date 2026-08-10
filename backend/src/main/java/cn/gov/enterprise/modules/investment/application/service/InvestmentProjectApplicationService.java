package cn.gov.enterprise.modules.investment.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentProjectCommand;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentProjectRepository;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** First application use cases for InvestmentProject persistence. */
@Service
public class InvestmentProjectApplicationService {
    private final InvestmentProjectRepository repository;
    private final InvestmentIdentityGenerator identityGenerator;
    private final ProjectAccessPolicy projectAccessPolicy;

    public InvestmentProjectApplicationService(
            InvestmentProjectRepository repository,
            InvestmentIdentityGenerator identityGenerator,
            ProjectAccessPolicy projectAccessPolicy) {
        this.repository = repository;
        this.identityGenerator = identityGenerator;
        this.projectAccessPolicy = projectAccessPolicy;
    }

    @PreAuthorize("hasAuthority('investment:create')")
    @Transactional
    public InvestmentProject createInvestmentProject(CreateInvestmentProjectCommand command) {
        if (command == null) {
            throw new BusinessException("B0600", "投资事项创建参数不能为空");
        }
        ProjectEntity project = projectAccessPolicy.requireAccessible(command.projectId());
        if (!"01".equals(project.getProjectType())) {
            throw new BusinessException("B0600", "只有投资类项目可创建投资事项");
        }
        if (command.investmentMethod() == InvestmentProject.InvestmentMethod.LEGACY_UNKNOWN) {
            throw new BusinessException("B0600", "新投资事项不允许使用历史未知出资方式");
        }
        if (repository.existsByInvestmentNo(command.investmentNo())) {
            throw new BusinessException("B0609", "投资编号已存在");
        }
        if (repository.findByProjectId(command.projectId()).isPresent()) {
            throw new BusinessException("B0609", "项目已关联投资事项");
        }
        InvestmentProject investmentProject = new InvestmentProject(
                identityGenerator.nextId(), command.projectId(), command.investmentNo(),
                command.investmentName(), command.investmentType(), command.investmentMethod(),
                command.investmentAmount(), command.investmentRatio(), project.getDepartmentId(),
                project.getLeaderId(), InvestmentProject.Status.DRAFT);
        repository.save(investmentProject);
        return investmentProject;
    }

    @PreAuthorize("hasAuthority('investment:view')")
    @Transactional(readOnly = true)
    public InvestmentProject queryInvestmentProject(Long investmentProjectId) {
        Long projectId = repository.findProjectIdById(investmentProjectId)
                .orElseThrow(() -> new BusinessException("B0604", "投资事项不存在"));
        projectAccessPolicy.requireAccessible(projectId);
        InvestmentProject investmentProject = repository.findById(investmentProjectId)
                .orElseThrow(() -> new BusinessException("B0604", "投资事项不存在"));
        if (!projectId.equals(investmentProject.projectId())) {
            throw new BusinessException("B0600", "投资事项关联项目在查询期间发生变更");
        }
        return investmentProject;
    }
}
