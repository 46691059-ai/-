package cn.gov.enterprise.modules.investment.application.command;

import cn.gov.enterprise.modules.investment.domain.model.InvestmentOpportunity;
import java.math.BigDecimal;

/** Application input for registering an investment opportunity. */
public record CreateInvestmentOpportunityCommand(
        String opportunityNo,
        String opportunityName,
        InvestmentOpportunity.Source source,
        Long proposingOrgId,
        String investmentDirection,
        String partnerName,
        BigDecimal preliminaryReturn) {
}
