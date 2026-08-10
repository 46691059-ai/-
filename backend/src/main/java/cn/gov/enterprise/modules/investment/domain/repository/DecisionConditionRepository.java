package cn.gov.enterprise.modules.investment.domain.repository;

import cn.gov.enterprise.modules.investment.domain.model.DecisionCondition;
import java.util.List;
import java.util.Optional;

public interface DecisionConditionRepository {
    void save(DecisionCondition condition);
    Optional<DecisionCondition> findByIdForUpdate(Long id);
    List<DecisionCondition> findByDecisionId(Long decisionId);
    boolean existsByConditionNo(Long decisionId, String conditionNo);
    boolean sourceNodeBelongsToDecision(Long sourceNodeId, Long decisionId);
    boolean assignmentTargetsExist(Long orgId, Long userId);
    void update(DecisionCondition condition, String rectificationSummary, Long evidenceFileId,
                Long reviewerId, String reviewResult, String reviewOpinion);
    void saveAction(Long actionId, DecisionCondition before, DecisionCondition after,
                    Long operatorId, String opinion, Long evidenceFileId, String idempotencyKey);
    boolean hasOpenBlockingConditions(Long decisionId);
    boolean hasOpenMajorRisk(Long decisionId);
}
