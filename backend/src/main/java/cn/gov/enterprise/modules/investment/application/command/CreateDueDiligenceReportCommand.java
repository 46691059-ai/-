package cn.gov.enterprise.modules.investment.application.command;

import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceReport;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceType;
import java.time.LocalDate;

public record CreateDueDiligenceReportCommand(
        DueDiligenceType type, String reportNo, String reportName, String entrustedOrg,
        Long leadPersonId, LocalDate startDate, LocalDate endDate, LocalDate baseDate,
        DueDiligenceReport.Conclusion conclusion, String conclusionSummary,
        Long primaryFileId) {
}
