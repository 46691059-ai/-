package cn.gov.enterprise.modules.investment.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable investment scheme version including its funding allocation snapshot. */
public record InvestmentSchemeVersion(
        Long id,
        Long schemeId,
        int versionNo,
        String schemeNo,
        String schemeName,
        Long investmentSubjectOrgId,
        Long investeeCompanyId,
        InvestmentProject.InvestmentType investmentType,
        BigDecimal totalAmount,
        String currencyCode,
        InvestmentProject.InvestmentMethod investmentMethod,
        String contributionScheduleSummary,
        BigDecimal preInvestmentRatio,
        BigDecimal postInvestmentRatio,
        String shareType,
        String controlType,
        String governanceArrangement,
        String cooperationMode,
        String partnerArrangement,
        BigDecimal valuationAmount,
        LocalDate valuationBaseDate,
        String incomeDistribution,
        String exitType,
        String exitPlan,
        String conditionsPrecedent,
        Long feasibilityVersionId,
        Long dueDiligencePackageId,
        Long primaryFileId,
        Status status,
        List<SchemeFunding> fundingSources,
        int version) {

    public InvestmentSchemeVersion {
        InvestmentProject.requirePositiveIfPresent(id, "Scheme version id");
        InvestmentProject.requirePositive(schemeId, "Scheme id");
        if (versionNo <= 0) throw new IllegalArgumentException("Scheme version number must be positive");
        schemeNo = normalize(schemeNo);
        schemeName = InvestmentProject.requireText(schemeName, "Scheme name");
        InvestmentProject.requirePositive(investmentSubjectOrgId, "Investment subject organization id");
        InvestmentProject.requirePositiveIfPresent(investeeCompanyId, "Investee company id");
        Objects.requireNonNull(investmentType, "Investment type must not be null");
        totalAmount = InvestmentProject.requireNonNegative(totalAmount, "Total amount");
        currencyCode = InvestmentProject.requireText(currencyCode, "Currency code").toUpperCase(Locale.ROOT);
        if (currencyCode.length() != 3) throw new IllegalArgumentException("Currency code must contain 3 letters");
        Objects.requireNonNull(investmentMethod, "Investment method must not be null");
        preInvestmentRatio = optionalRatio(preInvestmentRatio, "Pre-investment ratio");
        postInvestmentRatio = optionalRatio(postInvestmentRatio, "Post-investment ratio");
        valuationAmount = optionalNonNegative(valuationAmount, "Valuation amount");
        InvestmentProject.requirePositive(feasibilityVersionId, "Feasibility version id");
        InvestmentProject.requirePositive(dueDiligencePackageId, "Due diligence package id");
        InvestmentProject.requirePositiveIfPresent(primaryFileId, "Primary file id");
        Objects.requireNonNull(status, "Scheme version status must not be null");
        fundingSources = fundingSources == null ? List.of() : List.copyOf(fundingSources);
        if (fundingSources.isEmpty()) throw new IllegalArgumentException("Funding sources must not be empty");
        BigDecimal fundingTotal = fundingSources.stream()
                .map(SchemeFunding::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (fundingTotal.compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException("Funding amount total must equal scheme total amount");
        }
        if (version < 0) throw new IllegalArgumentException("Version must not be negative");
    }

    public enum Status { DRAFT, SUBMITTED, FROZEN, SUPERSEDED }

    private static BigDecimal optionalRatio(BigDecimal value, String label) {
        if (value != null && (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException(label + " must be between 0 and 100");
        }
        return value;
    }

    private static BigDecimal optionalNonNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) throw new IllegalArgumentException(label + " must not be negative");
        return value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
