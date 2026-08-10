package cn.gov.enterprise.modules.investment.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;

/** One funding source belonging to an immutable scheme version. */
public record SchemeFunding(
        Long id,
        Long schemeVersionId,
        String fundingType,
        String providerName,
        BigDecimal amount,
        BigDecimal ratio,
        BigDecimal costRate,
        LocalDate availableDate,
        boolean confirmed,
        Long evidenceFileId,
        int version) {

    public SchemeFunding {
        InvestmentProject.requirePositiveIfPresent(id, "Funding id");
        InvestmentProject.requirePositive(schemeVersionId, "Scheme version id");
        fundingType = InvestmentProject.requireText(fundingType, "Funding type").toUpperCase(Locale.ROOT);
        providerName = normalize(providerName);
        amount = InvestmentProject.requireNonNegative(amount, "Funding amount");
        if (ratio == null || ratio.signum() < 0 || ratio.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Funding ratio must be between 0 and 100");
        }
        ratio = ratio.setScale(4, RoundingMode.HALF_UP);
        if (costRate != null && costRate.signum() < 0) {
            throw new IllegalArgumentException("Funding cost rate must not be negative");
        }
        InvestmentProject.requirePositiveIfPresent(evidenceFileId, "Evidence file id");
        if (version < 0) throw new IllegalArgumentException("Version must not be negative");
    }

    /** Derived percentage; it is deliberately not persisted as duplicate business data. */
    public static BigDecimal calculateRatio(BigDecimal amount, BigDecimal totalAmount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Funding amount must be non-negative");
        }
        if (totalAmount == null || totalAmount.signum() < 0) {
            throw new IllegalArgumentException("Total amount must be non-negative");
        }
        if (totalAmount.signum() == 0) return BigDecimal.ZERO;
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(totalAmount, 4, RoundingMode.HALF_UP);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
