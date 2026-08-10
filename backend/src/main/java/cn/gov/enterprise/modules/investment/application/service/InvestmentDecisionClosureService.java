package cn.gov.enterprise.modules.investment.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentDecisionCase;
import cn.gov.enterprise.modules.investment.domain.repository.DecisionConditionRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentDecisionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentDecisionClosureService {
    private final InvestmentDecisionRepository decisions;
    private final DecisionConditionRepository conditions;
    private final InvestmentLifecycleStagePolicy lifecycle;

    public InvestmentDecisionClosureService(InvestmentDecisionRepository decisions,
            DecisionConditionRepository conditions, InvestmentLifecycleStagePolicy lifecycle) {
        this.decisions = decisions;
        this.conditions = conditions;
        this.lifecycle = lifecycle;
    }

    @PreAuthorize("hasAuthority('investment:decision:archive')")
    @Transactional
    public InvestmentDecisionCase archive(Long decisionId) {
        InvestmentDecisionCase before = decisions.findByIdForUpdate(decisionId)
                .orElseThrow(() -> new BusinessException("B0684", "投资决策不存在"));
        lifecycle.requireStage(before.investmentId(), InvestmentLifecycleStagePolicy.DECISION);
        if (conditions.hasOpenBlockingConditions(decisionId)) {
            throw new BusinessException("B0695", "存在未关闭的阻断性整改条件，禁止归档");
        }
        if (conditions.hasOpenMajorRisk(decisionId)) {
            throw new BusinessException("B0696", "存在未关闭的重大风险，禁止归档");
        }
        InvestmentDecisionCase archived;
        try {
            archived = before.transitionTo(InvestmentDecisionCase.Status.ARCHIVED);
        } catch (IllegalStateException exception) {
            throw new BusinessException("B0697", "只有已批准决策可以归档");
        }
        decisions.updateDecisionState(decisionId, archived.status(), archived.currentSnapshotId());
        return archived;
    }
}
