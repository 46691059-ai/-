package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DecisionConditionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DecisionConditionMapper extends BaseMapperX<DecisionConditionEntity> {
    @Select("SELECT * FROM investment_decision_condition WHERE id=#{id} AND deleted=0 FOR UPDATE")
    DecisionConditionEntity selectForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) > 0 FROM investment_decision_node WHERE id=#{nodeId} AND decision_id=#{decisionId} AND deleted=0")
    boolean sourceNodeBelongsToDecision(@Param("nodeId") Long sourceNodeId,
                                        @Param("decisionId") Long decisionId);

    @Select("""
        SELECT CASE WHEN
          EXISTS(SELECT 1 FROM sys_org o WHERE o.id=#{orgId} AND o.status=1 AND o.deleted=0)
          AND EXISTS(SELECT 1 FROM sys_user u WHERE u.id=#{userId} AND u.status=1 AND u.deleted=0)
        THEN 1 ELSE 0 END
        """)
    boolean assignmentTargetsExist(@Param("orgId") Long orgId, @Param("userId") Long userId);

    @Select("""
        SELECT CASE WHEN
          EXISTS(SELECT 1 FROM investment_decision d WHERE d.id=#{decisionId} AND d.deleted=0
                 AND d.risk_level_snapshot IN ('MAJOR','HIGH','CRITICAL')
                 AND COALESCE(d.risk_gate_result,'PENDING') NOT IN ('PASSED','NOT_REQUIRED'))
          OR EXISTS(SELECT 1 FROM investment_decision_condition c WHERE c.decision_id=#{decisionId}
                 AND c.deleted=0 AND c.risk_level_snapshot IN ('MAJOR','HIGH','CRITICAL')
                 AND c.status NOT IN ('CLOSED','WAIVED','CANCELLED'))
        THEN 1 ELSE 0 END
        """)
    boolean hasOpenMajorRisk(@Param("decisionId") Long decisionId);
}
