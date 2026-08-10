package cn.gov.enterprise.modules.investment.domain.model;

import java.util.Objects;

/** Investment scheme archive. Version contents are stored as immutable child records. */
public record InvestmentScheme(
        Long id,
        Long investmentProjectId,
        Long currentVersionId,
        Long currentFrozenVersionId,
        Status status,
        int version) {

    public InvestmentScheme {
        InvestmentProject.requirePositiveIfPresent(id, "Scheme id");
        InvestmentProject.requirePositive(investmentProjectId, "Investment project id");
        InvestmentProject.requirePositiveIfPresent(currentVersionId, "Current version id");
        InvestmentProject.requirePositiveIfPresent(currentFrozenVersionId, "Frozen version id");
        Objects.requireNonNull(status, "Scheme status must not be null");
        if (version < 0) throw new IllegalArgumentException("Version must not be negative");
    }

    public InvestmentScheme useVersion(Long schemeVersionId) {
        InvestmentProject.requirePositive(schemeVersionId, "Scheme version id");
        return new InvestmentScheme(id, investmentProjectId, schemeVersionId,
                currentFrozenVersionId, Status.DRAFT, version + 1);
    }

    public enum Status { NOT_STARTED, DRAFT, FROZEN, CLOSED }
}
