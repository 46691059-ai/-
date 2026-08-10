package cn.gov.enterprise.modules.investment.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.command.CreateDecisionConditionCommand;
import cn.gov.enterprise.modules.investment.application.command.ReviewDecisionConditionCommand;
import cn.gov.enterprise.modules.investment.application.command.SubmitConditionRectificationCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy;
import cn.gov.enterprise.modules.investment.domain.model.DecisionCondition;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentDecisionCase;
import cn.gov.enterprise.modules.investment.domain.repository.DecisionConditionRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentDecisionRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.security.CurrentSecurityContext;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentDecisionConditionService {
    private final DecisionConditionRepository conditions;
    private final InvestmentDecisionRepository decisions;
    private final InvestmentIdentityGenerator ids;
    private final InvestmentLifecycleStagePolicy lifecycle;
    private final CurrentSecurityContext security;

    public InvestmentDecisionConditionService(DecisionConditionRepository conditions,
            InvestmentDecisionRepository decisions, InvestmentIdentityGenerator ids,
            InvestmentLifecycleStagePolicy lifecycle, CurrentSecurityContext security) {
        this.conditions = conditions;
        this.decisions = decisions;
        this.ids = ids;
        this.lifecycle = lifecycle;
        this.security = security;
    }

    @PreAuthorize("hasAuthority('investment:decision:condition')")
    @Transactional
    public DecisionCondition create(Long decisionId, CreateDecisionConditionCommand command) {
        InvestmentDecisionCase decision = requireDecision(decisionId, true);
        if (decision.status() != InvestmentDecisionCase.Status.APPROVED) {
            throw new BusinessException("B0690", "只有Workflow已批准的决策才能创建附加条件");
        }
        if (conditions.existsByConditionNo(decisionId, command.conditionNo())) {
            throw new BusinessException("B0691", "条件编号已存在");
        }
        if (!conditions.sourceNodeBelongsToDecision(command.sourceNodeId(), decisionId)) {
            throw new BusinessException("B0698", "条件来源节点不属于当前投资决策");
        }
        if (!conditions.assignmentTargetsExist(command.responsibleOrgId(), command.responsiblePersonId())) {
            throw new BusinessException("B0699", "整改责任组织或责任人无效");
        }
        DecisionCondition condition = new DecisionCondition(ids.nextId(), decisionId,
                decision.currentSnapshotId(), command.sourceNodeId(), command.conditionNo(),
                command.content(), command.blocking(), command.responsibleOrgId(),
                command.responsiblePersonId(), command.deadline(), normalizeRisk(command.riskLevel()),
                DecisionCondition.Status.OPEN, 0);
        conditions.save(condition);
        conditions.saveAction(ids.nextId(), null, condition, security.userId(),
                "创建附条件整改任务", null, UUID.randomUUID().toString());
        return condition;
    }

    @PreAuthorize("hasAuthority('investment:decision:condition')")
    @Transactional
    public DecisionCondition start(Long conditionId) {
        DecisionCondition before = requireCondition(conditionId);
        requireDecision(before.decisionId(), true);
        DecisionCondition after = before.transitionTo(DecisionCondition.Status.IN_PROGRESS);
        conditions.update(after, null, null, null, null, null);
        conditions.saveAction(ids.nextId(), before, after, security.userId(),
                "开始整改", null, UUID.randomUUID().toString());
        return after;
    }

    @PreAuthorize("hasAuthority('investment:decision:condition')")
    @Transactional
    public DecisionCondition submit(Long conditionId, SubmitConditionRectificationCommand command) {
        DecisionCondition before = requireCondition(conditionId);
        requireDecision(before.decisionId(), true);
        DecisionCondition after = before.transitionTo(DecisionCondition.Status.SUBMITTED);
        conditions.update(after, command.rectificationSummary(), command.evidenceFileId(), null, null, null);
        conditions.saveAction(ids.nextId(), before, after, security.userId(),
                command.rectificationSummary(), command.evidenceFileId(), idempotency(command.idempotencyKey()));
        return after;
    }

    @PreAuthorize("hasAuthority('investment:decision:condition')")
    @Transactional
    public DecisionCondition review(Long conditionId, ReviewDecisionConditionCommand command) {
        DecisionCondition before = requireCondition(conditionId);
        requireDecision(before.decisionId(), true);
        DecisionCondition.Status target = command.result() == ReviewDecisionConditionCommand.ReviewResult.APPROVED
                ? DecisionCondition.Status.VERIFIED : DecisionCondition.Status.REJECTED;
        DecisionCondition after = before.transitionTo(target);
        conditions.update(after, null, null, security.userId(), command.result().name(), command.opinion());
        conditions.saveAction(ids.nextId(), before, after, security.userId(), command.opinion(), null,
                idempotency(command.idempotencyKey()));
        return after;
    }

    @PreAuthorize("hasAuthority('investment:decision:condition')")
    @Transactional
    public DecisionCondition close(Long conditionId) {
        DecisionCondition before = requireCondition(conditionId);
        requireDecision(before.decisionId(), true);
        DecisionCondition after = before.transitionTo(DecisionCondition.Status.CLOSED);
        conditions.update(after, null, null, null, null, null);
        conditions.saveAction(ids.nextId(), before, after, security.userId(),
                "复核通过并关闭", null, UUID.randomUUID().toString());
        return after;
    }

    @PreAuthorize("hasAuthority('investment:decision:view')")
    @Transactional(readOnly = true)
    public List<DecisionCondition> list(Long decisionId) {
        requireDecision(decisionId, false);
        return conditions.findByDecisionId(decisionId);
    }

    private InvestmentDecisionCase requireDecision(Long id, boolean lock) {
        InvestmentDecisionCase decision = (lock ? decisions.findByIdForUpdate(id) : decisions.findById(id))
                .orElseThrow(() -> new BusinessException("B0684", "投资决策不存在"));
        lifecycle.requireStage(decision.investmentId(), InvestmentLifecycleStagePolicy.DECISION);
        return decision;
    }

    private DecisionCondition requireCondition(Long id) {
        return conditions.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("B0692", "附条件整改任务不存在"));
    }

    private static String normalizeRisk(String risk) {
        return risk == null || risk.isBlank() ? "UNKNOWN" : risk.toUpperCase(java.util.Locale.ROOT);
    }

    private static String idempotency(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}
