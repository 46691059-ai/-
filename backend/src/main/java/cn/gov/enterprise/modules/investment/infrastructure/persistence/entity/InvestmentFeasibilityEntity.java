package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("investment_feasibility")
public class InvestmentFeasibilityEntity extends BaseIdEntity {
    private Long investmentId;
    private Long currentVersionId;
    private Long currentFrozenVersionId;
    private String status;
    private String marketAnalysis;
    private String technicalAnalysis;
    private String financialAnalysis;
    private String riskAnalysis;
    private Integer investmentPeriod;
    private BigDecimal annualIncome;
    private BigDecimal annualCost;
    private BigDecimal annualProfit;
    private BigDecimal roi;
    private BigDecimal irr;
    private BigDecimal paybackPeriod;
    private String attachment;
}
