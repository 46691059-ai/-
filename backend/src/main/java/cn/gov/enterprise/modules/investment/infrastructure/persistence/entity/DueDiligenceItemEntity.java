package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("investment_due_diligence_item")
public class DueDiligenceItemEntity extends BaseIdEntity {
    private Long dueDiligenceId;
    private Long investmentId;
    private String itemNo;
    private String category;
    private String severity;
    private Integer blockingFlag;
    private String problemDescription;
    private String impactDescription;
    private String rectificationMeasure;
    private Long responsibleOrgId;
    private Long responsiblePersonId;
    private LocalDate deadline;
    private String status;
    private String resolutionSummary;
    private Long evidenceFileId;
    private Long reviewerId;
    private String reviewResult;
    private String reviewOpinion;
    private LocalDateTime reviewedTime;
    private String riskAcceptanceRef;
    private LocalDateTime closedTime;
}
