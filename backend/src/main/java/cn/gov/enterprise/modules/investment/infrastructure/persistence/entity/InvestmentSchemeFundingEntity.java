package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("investment_scheme_funding")
public class InvestmentSchemeFundingEntity extends BaseIdEntity {
    private Long schemeVersionId;
    private String fundingType;
    private String providerName;
    private BigDecimal amount;
    private BigDecimal costRate;
    private LocalDate availableDate;
    private Integer confirmedFlag;
    private Long evidenceFileId;
}
