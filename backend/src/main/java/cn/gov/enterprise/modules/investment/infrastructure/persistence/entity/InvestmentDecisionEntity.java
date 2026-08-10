package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("investment_decision")
public class InvestmentDecisionEntity extends BaseIdEntity {
    private Long investmentId;
    private String decisionNo;
    private String decisionSubject;
    private String decisionType;
    private LocalDate meetingDate;
    private String decisionResult;
    private Long schemeVersionId;
    private String schemeContentHash;
    private Long feasibilityVersionId;
    private Long dueDiligencePackageId;
    private Integer decisionPackageVersion;
    private String routeRuleVersion;
    private String routeSnapshotHash;
    private Integer majorDecisionApplicable;
    private Integer partyPreStudyRequired;
    private String approvalStatus;
    private LocalDateTime submittedTime;
    private LocalDateTime completedTime;
    private Long currentSnapshotId;
}
