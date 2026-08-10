package cn.gov.enterprise.modules.investment.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** Due-diligence issue and remediation tracking item. */
public record DueDiligenceItem(
        Long id, Long reportId, Long investmentProjectId, String itemNo, String category,
        Severity severity, boolean blocking, String problemDescription,
        String impactDescription, String rectificationMeasure, Long responsibleOrgId,
        Long responsiblePersonId, LocalDate deadline, Status status, int version) {

    public DueDiligenceItem {
        InvestmentProject.requirePositiveIfPresent(id, "Due diligence item id");
        InvestmentProject.requirePositive(reportId, "Report id");
        InvestmentProject.requirePositive(investmentProjectId, "Investment project id");
        itemNo = InvestmentProject.requireText(itemNo, "Item number");
        category = InvestmentProject.requireText(category, "Item category");
        Objects.requireNonNull(severity, "Severity must not be null");
        problemDescription = InvestmentProject.requireText(problemDescription, "Problem description");
        InvestmentProject.requirePositiveIfPresent(responsibleOrgId, "Responsible organization id");
        InvestmentProject.requirePositiveIfPresent(responsiblePersonId, "Responsible person id");
        Objects.requireNonNull(status, "Item status must not be null");
        if (version < 0) throw new IllegalArgumentException("Version must not be negative");
    }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum Status { OPEN, RECTIFYING, PENDING_REVIEW, CLOSED, ACCEPTED }
}
