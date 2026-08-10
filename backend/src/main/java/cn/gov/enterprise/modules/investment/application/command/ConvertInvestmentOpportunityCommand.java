package cn.gov.enterprise.modules.investment.application.command;

import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Explicit conversion parameters; no material business value is inferred from names. */
public record ConvertInvestmentOpportunityCommand(
        String projectNo,
        String projectMode,
        LocalDate startDate,
        LocalDate endDate,
        String investmentNo,
        InvestmentProject.InvestmentType investmentType,
        InvestmentProject.InvestmentMethod investmentMethod,
        BigDecimal investmentAmount,
        BigDecimal investmentRatio,
        BigDecimal expectedProfit,
        String riskLevel,
        String remark) {
}
