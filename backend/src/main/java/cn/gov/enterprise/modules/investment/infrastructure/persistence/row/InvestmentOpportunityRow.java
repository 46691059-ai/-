package cn.gov.enterprise.modules.investment.infrastructure.persistence.row;

import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentOpportunityEntity;
import lombok.Getter;
import lombok.Setter;

/** Opportunity persistence row with the converted Project identity resolved in one scoped query. */
@Getter
@Setter
public class InvestmentOpportunityRow extends InvestmentOpportunityEntity {
    private Long convertedProjectId;
}
