package cn.gov.enterprise.modules.investment.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** Investment opportunity before conversion into Project and InvestmentProject aggregates. */
public record InvestmentOpportunity(
        Long id,
        String opportunityNo,
        String opportunityName,
        Source source,
        Long proposingOrgId,
        Long ownerId,
        String investmentDirection,
        String partnerName,
        BigDecimal preliminaryReturn,
        Status status,
        String reviewConclusion,
        Long convertedProjectId,
        Long convertedInvestmentProjectId,
        LocalDateTime convertedAt,
        Integer version) {

    public InvestmentOpportunity {
        InvestmentProject.requirePositiveIfPresent(id, "Opportunity id");
        opportunityNo = InvestmentProject.requireText(opportunityNo, "Opportunity number");
        opportunityName = InvestmentProject.requireText(opportunityName, "Opportunity name");
        Objects.requireNonNull(source, "Opportunity source must not be null");
        InvestmentProject.requirePositive(proposingOrgId, "Proposing organization id");
        InvestmentProject.requirePositive(ownerId, "Opportunity owner id");
        investmentDirection = InvestmentProject.requireText(
                investmentDirection, "Investment direction");
        partnerName = normalize(partnerName);
        preliminaryReturn = InvestmentProject.requireNonNegative(
                preliminaryReturn, "Preliminary return");
        Objects.requireNonNull(status, "Opportunity status must not be null");
        reviewConclusion = normalize(reviewConclusion);
        InvestmentProject.requirePositiveIfPresent(convertedProjectId, "Converted project id");
        InvestmentProject.requirePositiveIfPresent(
                convertedInvestmentProjectId, "Converted investment project id");
        if (version == null || version < 0) {
            throw new IllegalArgumentException("Opportunity version must not be negative");
        }
        boolean converted = status == Status.CONVERTED;
        if (converted != (convertedProjectId != null
                && convertedInvestmentProjectId != null
                && convertedAt != null)) {
            throw new IllegalArgumentException(
                    "Converted opportunity must reference both resulting aggregates and conversion time");
        }
        if (!converted && (convertedProjectId != null
                || convertedInvestmentProjectId != null
                || convertedAt != null)) {
            throw new IllegalArgumentException("Non-converted opportunity cannot hold conversion results");
        }
    }

    /** Submit a newly registered or rejected opportunity for screening. */
    public InvestmentOpportunity submit() {
        requireStatus(Status.REGISTERED, Status.REJECTED);
        return transition(Status.SCREENING, null);
    }

    /** Approve the current review gate and enter analysis or evaluation. */
    public InvestmentOpportunity approveReview(String conclusion) {
        String normalizedConclusion = InvestmentProject.requireText(
                conclusion, "Review conclusion");
        if (status == Status.SCREENING) {
            return transition(Status.ANALYSING, normalizedConclusion);
        }
        if (status == Status.ANALYSING) {
            return transition(Status.EVALUATING, normalizedConclusion);
        }
        throw invalidTransition("approve review");
    }

    /** Reject a reviewable opportunity. Re-submission always starts from screening. */
    public InvestmentOpportunity reject(String conclusion) {
        String normalizedConclusion = InvestmentProject.requireText(
                conclusion, "Rejection conclusion");
        requireStatus(Status.SCREENING, Status.ANALYSING, Status.EVALUATING);
        return transition(Status.REJECTED, normalizedConclusion);
    }

    public InvestmentOpportunity resubmit() {
        requireStatus(Status.REJECTED);
        return transition(Status.SCREENING, null);
    }

    public InvestmentOpportunity close(String conclusion) {
        if (status == Status.CONVERTED || status == Status.CLOSED) {
            throw invalidTransition("close");
        }
        return transition(
                Status.CLOSED,
                InvestmentProject.requireText(conclusion, "Close conclusion"));
    }

    public InvestmentOpportunity convert(
            Long projectId, Long investmentProjectId, LocalDateTime conversionTime) {
        requireStatus(Status.EVALUATING);
        InvestmentProject.requirePositive(projectId, "Converted project id");
        InvestmentProject.requirePositive(investmentProjectId, "Converted investment project id");
        Objects.requireNonNull(conversionTime, "Conversion time must not be null");
        return new InvestmentOpportunity(
                id, opportunityNo, opportunityName, source, proposingOrgId, ownerId,
                investmentDirection, partnerName, preliminaryReturn, Status.CONVERTED,
                reviewConclusion, projectId, investmentProjectId, conversionTime, version + 1);
    }

    public enum Source {
        GOVERNMENT,
        ENTERPRISE_COOPERATION,
        MARKET_DISCOVERY,
        GROUP_ASSIGNED,
        SELF_PLANNED
    }

    public enum Status {
        REGISTERED,
        SCREENING,
        ANALYSING,
        EVALUATING,
        REJECTED,
        CONVERTED,
        CLOSED
    }

    private InvestmentOpportunity transition(Status target, String conclusion) {
        return new InvestmentOpportunity(
                id, opportunityNo, opportunityName, source, proposingOrgId, ownerId,
                investmentDirection, partnerName, preliminaryReturn, target, conclusion,
                null, null, null, version + 1);
    }

    private void requireStatus(Status... allowed) {
        for (Status candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw invalidTransition("transition");
    }

    private IllegalStateException invalidTransition(String action) {
        return new IllegalStateException(
                "Opportunity cannot " + action + " from status " + status);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
