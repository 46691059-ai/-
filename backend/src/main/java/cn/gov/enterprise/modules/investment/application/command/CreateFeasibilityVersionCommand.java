package cn.gov.enterprise.modules.investment.application.command;

import cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateFeasibilityVersionCommand(
        String reportNo, String reportName, FeasibilityVersion.CompilerType compilerType,
        Long compilerOrgId, String compilerOrgName, LocalDate preparedDate, LocalDate baseDate,
        BigDecimal totalInvestment, BigDecimal annualRevenue, BigDecimal annualCost,
        BigDecimal annualTax, BigDecimal annualNetProfit, BigDecimal roi, BigDecimal irr,
        BigDecimal paybackPeriod, FeasibilityVersion.RiskConclusion riskConclusion,
        FeasibilityVersion.Conclusion conclusion, String conclusionSummary, Long primaryFileId) {
}
