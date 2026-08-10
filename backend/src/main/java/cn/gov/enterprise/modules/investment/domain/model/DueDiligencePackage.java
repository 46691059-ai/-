package cn.gov.enterprise.modules.investment.domain.model;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Versioned due-diligence package with a frozen required-type snapshot. */
public record DueDiligencePackage(
        Long id, Long investmentProjectId, int packageVersion, String ruleVersion,
        Set<DueDiligenceType> requiredTypes, Conclusion overallConclusion,
        int openBlockingCount, Status status, int version) {

    public DueDiligencePackage {
        InvestmentProject.requirePositiveIfPresent(id, "Due diligence package id");
        InvestmentProject.requirePositive(investmentProjectId, "Investment project id");
        if (packageVersion <= 0) throw new IllegalArgumentException("Package version must be positive");
        ruleVersion = InvestmentProject.requireText(ruleVersion, "Rule version");
        requiredTypes = requiredTypes == null ? Set.of() : Set.copyOf(requiredTypes);
        if (requiredTypes.isEmpty()) throw new IllegalArgumentException("Required types must not be empty");
        Objects.requireNonNull(overallConclusion, "Overall conclusion must not be null");
        if (openBlockingCount < 0 || version < 0) throw new IllegalArgumentException("Count/version is invalid");
        Objects.requireNonNull(status, "Package status must not be null");
    }

    public String requiredTypesSnapshot() {
        return requiredTypes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    public enum Conclusion { PENDING, PASS, CONDITIONAL, FAIL }
    public enum Status { PLANNED, IN_PROGRESS, COMPLETED, FROZEN, CLOSED }
}
