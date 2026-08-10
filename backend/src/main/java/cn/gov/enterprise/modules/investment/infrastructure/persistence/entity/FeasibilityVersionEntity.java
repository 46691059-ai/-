package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("investment_feasibility_version")
public class FeasibilityVersionEntity extends BaseIdEntity {
    private Long feasibilityId;
    private Integer versionNo;
    private String reportNo;
    private String reportName;
    private String compilerType;
    private Long compilerOrgId;
    private String compilerOrgName;
    private LocalDate preparedDate;
    private LocalDate baseDate;
    private String currencyCode;
    private String marketAnalysis;
    private String technicalAnalysis;
    private String financialAnalysis;
    private String riskAnalysis;
    private BigDecimal totalInvestment;
    private BigDecimal ownCapital;
    private BigDecimal financingAmount;
    private Integer constructionPeriodMonths;
    private Integer operationPeriodMonths;
    private BigDecimal annualRevenue;
    private BigDecimal annualCost;
    private BigDecimal annualTax;
    private BigDecimal annualNetProfit;
    private BigDecimal netPresentValue;
    private BigDecimal roi;
    private BigDecimal irr;
    private BigDecimal paybackPeriod;
    private BigDecimal discountRate;
    private String calculationAssumption;
    private String riskConclusion;
    private String conclusion;
    private String conclusionSummary;
    private String approvalInstanceRef;
    private Long primaryFileId;
    private String status;
    private String contentHash;
    private LocalDateTime frozenTime;
}
