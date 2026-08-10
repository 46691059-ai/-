package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("investment_decision_condition")
public class DecisionConditionEntity extends BaseIdEntity {
    private Long decisionId; private Long snapshotId; private Long sourceNodeId;
    private String conditionNo; private String conditionType; private String conditionContent;
    private Integer blockingFlag; private Long responsibleOrgId; private Long responsiblePersonId;
    private LocalDate deadline; private String status; private String rectificationSummary;
    private Long evidenceFileId; private LocalDateTime submittedTime; private Long reviewerId;
    private String reviewResult; private String reviewOpinion; private LocalDateTime reviewedTime;
    private LocalDateTime closedTime; private String sourceRiskRef; private String riskLevelSnapshot;
}
