package cn.gov.enterprise.modules.investment.application.command;

import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import java.math.BigDecimal;

/** Application input for the first InvestmentProject creation use case. */
public record CreateInvestmentProjectCommand(
        Long projectId,
        String investmentNo,
        String investmentName,
        InvestmentProject.InvestmentType investmentType,
        InvestmentProject.InvestmentMethod investmentMethod,
        BigDecimal investmentAmount,
        BigDecimal investmentRatio) {
}
