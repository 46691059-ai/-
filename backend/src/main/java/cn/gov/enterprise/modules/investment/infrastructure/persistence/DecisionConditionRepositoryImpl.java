package cn.gov.enterprise.modules.investment.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.domain.model.DecisionCondition;
import cn.gov.enterprise.modules.investment.domain.repository.DecisionConditionRepository;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DecisionConditionActionEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DecisionConditionEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.DecisionConditionActionMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.DecisionConditionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.stereotype.Repository;

@Repository
public class DecisionConditionRepositoryImpl implements DecisionConditionRepository {
    private final DecisionConditionMapper conditions;
    private final DecisionConditionActionMapper actions;

    public DecisionConditionRepositoryImpl(
            DecisionConditionMapper conditions, DecisionConditionActionMapper actions) {
        this.conditions = conditions;
        this.actions = actions;
    }

    @Override
    public void save(DecisionCondition condition) {
        DecisionConditionEntity entity = entity(condition);
        entity.setConditionType("REMEDIATION");
        entity.setDeleteToken(0L);
        conditions.insert(entity);
    }

    @Override
    public Optional<DecisionCondition> findByIdForUpdate(Long id) {
        return Optional.ofNullable(conditions.selectForUpdate(id)).map(this::domain);
    }

    @Override
    public List<DecisionCondition> findByDecisionId(Long decisionId) {
        return conditions.selectList(new LambdaQueryWrapper<DecisionConditionEntity>()
                        .eq(DecisionConditionEntity::getDecisionId, decisionId)
                        .orderByAsc(DecisionConditionEntity::getDeadline, DecisionConditionEntity::getId))
                .stream().map(this::domain).toList();
    }

    @Override
    public boolean existsByConditionNo(Long decisionId, String conditionNo) {
        return conditions.selectCount(new LambdaQueryWrapper<DecisionConditionEntity>()
                .eq(DecisionConditionEntity::getDecisionId, decisionId)
                .eq(DecisionConditionEntity::getConditionNo, conditionNo)) > 0;
    }

    @Override
    public boolean sourceNodeBelongsToDecision(Long sourceNodeId, Long decisionId) {
        return conditions.sourceNodeBelongsToDecision(sourceNodeId, decisionId);
    }

    @Override
    public boolean assignmentTargetsExist(Long orgId, Long userId) {
        return conditions.assignmentTargetsExist(orgId, userId);
    }

    @Override
    public void update(DecisionCondition condition, String rectificationSummary, Long evidenceFileId,
            Long reviewerId, String reviewResult, String reviewOpinion) {
        DecisionConditionEntity entity = entity(condition);
        entity.setRectificationSummary(rectificationSummary);
        entity.setEvidenceFileId(evidenceFileId);
        if (condition.status() == DecisionCondition.Status.SUBMITTED) entity.setSubmittedTime(LocalDateTime.now());
        if (reviewerId != null) {
            entity.setReviewerId(reviewerId);
            entity.setReviewResult(reviewResult);
            entity.setReviewOpinion(reviewOpinion);
            entity.setReviewedTime(LocalDateTime.now());
        }
        if (condition.status() == DecisionCondition.Status.CLOSED) entity.setClosedTime(LocalDateTime.now());
        if (conditions.updateById(entity) != 1) {
            throw new BusinessException("B0694", "附条件任务已被并发修改，请刷新后重试");
        }
    }

    @Override
    public void saveAction(Long actionId, DecisionCondition before, DecisionCondition after,
            Long operatorId, String opinion, Long evidenceFileId, String idempotencyKey) {
        DecisionConditionActionEntity entity = new DecisionConditionActionEntity();
        entity.setId(actionId);
        entity.setConditionId(after.id());
        entity.setActionType(after.status().name());
        entity.setFromStatus(before == null ? null : before.status().name());
        entity.setToStatus(after.status().name());
        entity.setOperatorId(operatorId);
        entity.setActionOpinion(opinion);
        entity.setEvidenceFileId(evidenceFileId);
        entity.setActionTime(LocalDateTime.now());
        entity.setTraceId(MDC.get("traceId"));
        entity.setIdempotencyKey(idempotencyKey);
        entity.setDeleteToken(0L);
        actions.insert(entity);
    }

    @Override
    public boolean hasOpenBlockingConditions(Long decisionId) {
        return conditions.selectCount(new LambdaQueryWrapper<DecisionConditionEntity>()
                .eq(DecisionConditionEntity::getDecisionId, decisionId)
                .eq(DecisionConditionEntity::getBlockingFlag, 1)
                .notIn(DecisionConditionEntity::getStatus, "CLOSED", "WAIVED", "CANCELLED")) > 0;
    }

    @Override
    public boolean hasOpenMajorRisk(Long decisionId) {
        return conditions.hasOpenMajorRisk(decisionId);
    }

    private DecisionConditionEntity entity(DecisionCondition condition) {
        DecisionConditionEntity entity = new DecisionConditionEntity();
        entity.setId(condition.id());
        entity.setDecisionId(condition.decisionId());
        entity.setSnapshotId(condition.snapshotId());
        entity.setSourceNodeId(condition.sourceNodeId());
        entity.setConditionNo(condition.conditionNo());
        entity.setConditionContent(condition.content());
        entity.setBlockingFlag(condition.blocking() ? 1 : 0);
        entity.setResponsibleOrgId(condition.responsibleOrgId());
        entity.setResponsiblePersonId(condition.responsiblePersonId());
        entity.setDeadline(condition.deadline());
        entity.setRiskLevelSnapshot(condition.riskLevel());
        entity.setStatus(condition.status().name());
        entity.setVersion(condition.version());
        return entity;
    }

    private DecisionCondition domain(DecisionConditionEntity entity) {
        return new DecisionCondition(entity.getId(), entity.getDecisionId(), entity.getSnapshotId(),
                entity.getSourceNodeId(), entity.getConditionNo(), entity.getConditionContent(),
                Integer.valueOf(1).equals(entity.getBlockingFlag()), entity.getResponsibleOrgId(),
                entity.getResponsiblePersonId(), entity.getDeadline(), entity.getRiskLevelSnapshot(),
                DecisionCondition.Status.fromStorage(entity.getStatus()),
                entity.getVersion() == null ? 0 : entity.getVersion());
    }
}
