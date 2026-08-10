package cn.gov.enterprise.modules.investment.application.security;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentProjectRepository;
import cn.gov.enterprise.modules.project.application.service.ProjectResourceAccessService;
import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import org.springframework.stereotype.Component;

/** Verifies project access and the expected lifecycle stage without advancing it. */
@Component
public class InvestmentLifecycleStagePolicy {
    public static final String FEASIBILITY = "FEASIBILITY";
    public static final String DUE_DILIGENCE = "DUE_DILIGENCE";
    public static final String DECISION = "DECISION";

    private final InvestmentProjectRepository investmentRepository;
    private final ProjectResourceAccessService projectAccessService;

    public InvestmentLifecycleStagePolicy(
            InvestmentProjectRepository investmentRepository,
            ProjectResourceAccessService projectAccessService) {
        this.investmentRepository = investmentRepository;
        this.projectAccessService = projectAccessService;
    }

    public Long requireStage(Long investmentId, String stageCode) {
        Long projectId = investmentRepository.findProjectIdById(investmentId)
                .orElseThrow(() -> new BusinessException("B0604", "投资事项不存在"));
        ProjectAggregate project = projectAccessService.requireAccessible(projectId);
        boolean present = project.getLifecycle().getStages().stream()
                .anyMatch(stage -> stageCode.equals(stage.getSnapshot().getStageCode()));
        if (!present) {
            throw new BusinessException("B0660", "项目生命周期缺少阶段：" + stageCode);
        }
        return projectId;
    }
}
