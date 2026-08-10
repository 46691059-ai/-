package cn.gov.enterprise.modules.investment.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** Immutable report version within one due-diligence package and discipline. */
public record DueDiligenceReport(
        Long id, Long packageId, Long investmentProjectId, DueDiligenceType type,
        int reportVersion, String reportNo, String reportName, String entrustedOrg,
        Long leadPersonId, LocalDate startDate, LocalDate endDate, LocalDate baseDate,
        Conclusion conclusion, String conclusionSummary, int materialRiskCount,
        int unresolvedRiskCount, Long primaryFileId, Status status, int version) {

    public DueDiligenceReport {
        InvestmentProject.requirePositiveIfPresent(id, "Due diligence report id");
        InvestmentProject.requirePositive(packageId, "Package id");
        InvestmentProject.requirePositive(investmentProjectId, "Investment project id");
        Objects.requireNonNull(type, "Due diligence type must not be null");
        if (reportVersion <= 0) throw new IllegalArgumentException("Report version must be positive");
        reportName = InvestmentProject.requireText(reportName, "Report name");
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Report dates are invalid");
        }
        Objects.requireNonNull(conclusion, "Report conclusion must not be null");
        if (materialRiskCount < 0 || unresolvedRiskCount < 0 || version < 0) {
            throw new IllegalArgumentException("Risk count/version is invalid");
        }
        InvestmentProject.requirePositiveIfPresent(primaryFileId, "Primary file id");
        Objects.requireNonNull(status, "Report status must not be null");
    }

    public enum Conclusion { PENDING, PASS, CONDITIONAL, FAIL }
    public enum Status { DRAFT, SUBMITTED, FROZEN, SUPERSEDED }
}
