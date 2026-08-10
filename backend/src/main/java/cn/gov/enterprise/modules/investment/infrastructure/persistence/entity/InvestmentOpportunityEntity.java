package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** MyBatis Plus mapping for investment_opportunity. */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("investment_opportunity")
public class InvestmentOpportunityEntity extends BaseIdEntity {
    private String opportunityNo;
    private Long investmentId;
    private String opportunityName;
    private String sourceType;
    private String sourceDescription;
    private Long proposingOrgId;
    private Long proposerId;
    private String investmentDirection;
    private String partnerSummary;
    private BigDecimal preliminaryAmount;
    private BigDecimal preliminaryIncome;
    private BigDecimal preliminaryRoi;
    private BigDecimal preliminaryIrr;
    private BigDecimal paybackPeriod;
    private LocalDate estimateDate;
    private String estimateAssumption;
    private String status;
    private String screeningConclusion;
    private LocalDateTime convertedTime;
}
