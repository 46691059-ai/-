package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("investment_scheme")
public class InvestmentSchemeEntity extends BaseIdEntity {
    private Long investmentId;
    private Long currentVersionId;
    private Long currentFrozenVersionId;
    private String status;
}
