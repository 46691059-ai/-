package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("investment_due_diligence")
public class DueDiligenceReportEntity extends BaseIdEntity {
    private Long packageId;
    private Long investmentId;
    private String dueDiligenceType;
    private Integer reportVersion;
    private String reportNo;
    private String reportName;
    private String entrustedOrg;
    private Long leadPersonId;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate baseDate;
    private String scopeSummary;
    private String methodologySummary;
    private String conclusion;
    private String conclusionSummary;
    private Integer materialRiskCount;
    private Integer unresolvedRiskCount;
    private String approvalInstanceRef;
    private Long primaryFileId;
    private String status;
    private String contentHash;
    private LocalDateTime frozenTime;
}
