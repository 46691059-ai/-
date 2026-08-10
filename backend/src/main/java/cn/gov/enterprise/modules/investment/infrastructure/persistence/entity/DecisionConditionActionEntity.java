package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("investment_decision_condition_action")
public class DecisionConditionActionEntity extends BaseIdEntity {
    private Long conditionId; private String actionType; private String fromStatus; private String toStatus;
    private Long operatorId; private String operatorRoleSnapshot; private String actionOpinion;
    private Long evidenceFileId; private LocalDateTime actionTime; private String traceId;
    private String idempotencyKey; private String payloadHash;
}
