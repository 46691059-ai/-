package cn.gov.enterprise.modules.investment.infrastructure.persistence.row;

public record DueDiligenceReferenceRow(
        Long investmentId, String status, String overallConclusion, Integer openBlockingCount) {
}
