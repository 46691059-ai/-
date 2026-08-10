package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** MyBatis Plus mapping for the V2.4 investment_project table. */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("investment_project")
public class InvestmentProjectEntity extends BaseIdEntity {
    private String investmentNo;
    private Long planId;
    private Long projectId;
    private String investmentName;
    private String investmentType;
    private String investmentMethod;
    private String cooperationMode;
    private String industry;
    private BigDecimal totalAmount;
    private BigDecimal ownCapital;
    private BigDecimal financingAmount;
    private BigDecimal investmentRatio;
    private String partnerName;
    private String partnerSummary;
    private Long spvCompanyId;
    private Long investeeCompanyId;
    private BigDecimal expectedIncome;
    private BigDecimal expectedRoi;
    private String riskLevel;
    private String approvalStatus;
    private String status;
}
