package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("investment_scheme_version")
public class InvestmentSchemeVersionEntity extends BaseIdEntity {
    private Long schemeId;
    private Integer versionNo;
    private String schemeNo;
    private String schemeName;
    private Long investmentSubjectOrgId;
    private Long investeeCompanyId;
    private String investmentType;
    private BigDecimal totalAmount;
    private String currencyCode;
    private String investmentMethod;
    private String contributionScheduleSummary;
    private BigDecimal preInvestmentRatio;
    private BigDecimal postInvestmentRatio;
    private String shareType;
    private String controlType;
    private String governanceArrangement;
    private String cooperationMode;
    private String partnerArrangement;
    private BigDecimal valuationAmount;
    private LocalDate valuationBaseDate;
    private String incomeDistribution;
    private String exitType;
    private String exitPlan;
    private String conditionsPrecedent;
    private Long feasibilityVersionId;
    private Long dueDiligencePackageId;
    private String status;
    private String contentHash;
    private LocalDateTime frozenTime;
    private Long primaryFileId;
}
