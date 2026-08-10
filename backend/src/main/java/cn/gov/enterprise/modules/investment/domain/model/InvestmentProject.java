package cn.gov.enterprise.modules.investment.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Investment aggregate boundary linked to the Project aggregate by identifier only.
 *
 * <p>The model is persistence- and framework-independent. Process behavior will be added
 * incrementally after the database model is approved.</p>
 */
public record InvestmentProject(
        Long id,
        Long projectId,
        String investmentNo,
        String investmentName,
        InvestmentType investmentType,
        InvestmentMethod investmentMethod,
        BigDecimal investmentAmount,
        BigDecimal investmentRatio,
        Long responsibleOrgId,
        Long ownerId,
        Status status) {

    public InvestmentProject {
        requirePositiveIfPresent(id, "Investment project id");
        requirePositive(projectId, "Project id");
        investmentNo = requireText(investmentNo, "Investment number");
        investmentName = requireText(investmentName, "Investment name");
        Objects.requireNonNull(investmentType, "Investment type must not be null");
        Objects.requireNonNull(investmentMethod, "Investment method must not be null");
        investmentAmount = requireNonNegative(investmentAmount, "Investment amount");
        investmentRatio = requirePercentageIfPresent(investmentRatio, "Investment ratio");
        requirePositive(responsibleOrgId, "Responsible organization id");
        requirePositive(ownerId, "Owner id");
        Objects.requireNonNull(status, "Investment status must not be null");
    }

    public enum InvestmentType {
        EQUITY,
        FIXED_ASSET,
        FUND,
        DATA_ASSET,
        OTHER
    }

    /** LEGACY_UNKNOWN is a migration sentinel and must not be used for new records. */
    public enum InvestmentMethod {
        CASH,
        ASSET,
        EQUITY,
        TECHNOLOGY,
        DATA_ASSET,
        MIXED,
        LEGACY_UNKNOWN
    }

    public enum Status {
        DRAFT,
        UNDER_REVIEW,
        APPROVED,
        IMPLEMENTING,
        POST_MANAGEMENT,
        EXITED,
        CLOSED
    }

    static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }

    static void requirePositive(Long value, String label) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    static void requirePositiveIfPresent(Long value, String label) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(label + " must be positive when present");
        }
    }

    static BigDecimal requireNonNegative(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
        return value;
    }

    static BigDecimal requirePercentageIfPresent(BigDecimal value, String label) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(label + " must be between 0 and 100");
        }
        return value;
    }
}
