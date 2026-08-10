package cn.gov.enterprise.modules.investment.domain.model;

import java.util.Objects;

/** Feasibility archive header. Report facts live in immutable FeasibilityVersion records. */
public record InvestmentFeasibility(
        Long id,
        Long investmentProjectId,
        Long currentVersionId,
        Long currentFrozenVersionId,
        Status status,
        int version) {

    public InvestmentFeasibility {
        InvestmentProject.requirePositiveIfPresent(id, "Feasibility id");
        InvestmentProject.requirePositive(investmentProjectId, "Investment project id");
        InvestmentProject.requirePositiveIfPresent(currentVersionId, "Current version id");
        InvestmentProject.requirePositiveIfPresent(currentFrozenVersionId, "Frozen version id");
        Objects.requireNonNull(status, "Feasibility status must not be null");
        if (version < 0) throw new IllegalArgumentException("Version must not be negative");
    }

    public InvestmentFeasibility useVersion(Long versionId) {
        InvestmentProject.requirePositive(versionId, "Feasibility version id");
        return new InvestmentFeasibility(
                id, investmentProjectId, versionId, currentFrozenVersionId,
                Status.IN_PROGRESS, version + 1);
    }

    public enum Status {
        NOT_STARTED, IN_PROGRESS, FROZEN, CLOSED, LEGACY
    }
}
