package cn.gov.enterprise.modules.investment.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable feasibility report version and its key economic indicators. */
public record FeasibilityVersion(
        Long id,
        Long feasibilityId,
        int versionNo,
        String reportNo,
        String reportName,
        CompilerType compilerType,
        Long compilerOrgId,
        String compilerOrgName,
        LocalDate preparedDate,
        LocalDate baseDate,
        BigDecimal totalInvestment,
        BigDecimal annualRevenue,
        BigDecimal annualCost,
        BigDecimal annualTax,
        BigDecimal annualNetProfit,
        BigDecimal roi,
        BigDecimal irr,
        BigDecimal paybackPeriod,
        RiskConclusion riskConclusion,
        Conclusion conclusion,
        String conclusionSummary,
        Long primaryFileId,
        Status status,
        int version) {

    public FeasibilityVersion {
        InvestmentProject.requirePositiveIfPresent(id, "Feasibility version id");
        InvestmentProject.requirePositive(feasibilityId, "Feasibility id");
        if (versionNo <= 0) throw new IllegalArgumentException("Version number must be positive");
        reportNo = normalize(reportNo);
        reportName = InvestmentProject.requireText(reportName, "Report name");
        Objects.requireNonNull(compilerType, "Compiler type must not be null");
        InvestmentProject.requirePositiveIfPresent(compilerOrgId, "Compiler organization id");
        compilerOrgName = InvestmentProject.requireText(compilerOrgName, "Compiler organization name");
        Objects.requireNonNull(baseDate, "Base date must not be null");
        totalInvestment = nonNegative(totalInvestment, "Total investment");
        annualRevenue = nonNegative(annualRevenue, "Annual revenue");
        annualCost = nonNegative(annualCost, "Annual cost");
        annualTax = nonNegative(annualTax, "Annual tax");
        annualNetProfit = Objects.requireNonNull(annualNetProfit, "Annual net profit must not be null");
        roi = optionalNonNegative(roi, "ROI");
        irr = optionalNonNegative(irr, "IRR");
        paybackPeriod = optionalNonNegative(paybackPeriod, "Payback period");
        Objects.requireNonNull(riskConclusion, "Risk conclusion must not be null");
        Objects.requireNonNull(conclusion, "Conclusion must not be null");
        conclusionSummary = normalize(conclusionSummary);
        InvestmentProject.requirePositiveIfPresent(primaryFileId, "Primary file id");
        Objects.requireNonNull(status, "Version status must not be null");
        if (version < 0) throw new IllegalArgumentException("Version must not be negative");
    }

    public enum CompilerType { INTERNAL, EXTERNAL, JOINT }
    public enum RiskConclusion { ACCEPTABLE, CONDITIONAL, UNACCEPTABLE }
    public enum Conclusion { PENDING, RECOMMENDED, NOT_RECOMMENDED }
    public enum Status { DRAFT, SUBMITTED, FROZEN, SUPERSEDED }

    private static BigDecimal nonNegative(BigDecimal value, String label) {
        return InvestmentProject.requireNonNegative(value, label);
    }

    private static BigDecimal optionalNonNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
