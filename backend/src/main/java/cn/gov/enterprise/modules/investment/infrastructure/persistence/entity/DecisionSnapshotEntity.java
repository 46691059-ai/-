package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("investment_decision_snapshot")
public class DecisionSnapshotEntity extends BaseIdEntity {
    private Long decisionId; private Integer snapshotVersion; private String snapshotStatus;
    private Integer decisionPackageVersion; private Long schemeVersionId; private String schemeContentHash;
    private Long feasibilityVersionId; private String feasibilityContentHash;
    private Long dueDiligencePackageId; private String dueDiligenceContentHash;
    private String routeCode; private String routeRuleVersion; private String routeSnapshotHash;
    private Integer majorDecisionApplicable; private Integer partyPreStudyRequired; private String finalDecisionBody;
    private String riskLevelSnapshot; private String riskGateResult; private String snapshotHash;
    private Long frozenBy; private LocalDateTime frozenTime;
}
