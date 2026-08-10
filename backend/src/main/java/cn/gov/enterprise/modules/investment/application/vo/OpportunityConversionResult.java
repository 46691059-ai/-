package cn.gov.enterprise.modules.investment.application.vo;

/** Stable idempotent response of an opportunity conversion. */
public record OpportunityConversionResult(
        Long opportunityId,
        Long projectId,
        Long investmentProjectId,
        boolean alreadyConverted) {
}
