package cn.gov.enterprise.modules.investment.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** Investment decision record without workflow-engine or persistence dependencies. */
public record InvestmentDecision(
        Long id,
        Long investmentProjectId,
        DecisionType decisionType,
        LocalDate decisionDate,
        Long decidedBy,
        Result result,
        String majorDecisionReference,
        String conditionalRequirements,
        Status status) {

    public InvestmentDecision {
        InvestmentProject.requirePositiveIfPresent(id, "Decision id");
        InvestmentProject.requirePositive(investmentProjectId, "Investment project id");
        Objects.requireNonNull(decisionType, "Decision type must not be null");
        Objects.requireNonNull(decisionDate, "Decision date must not be null");
        InvestmentProject.requirePositive(decidedBy, "Decision maker id");
        Objects.requireNonNull(result, "Decision result must not be null");
        majorDecisionReference = normalize(majorDecisionReference);
        conditionalRequirements = normalize(conditionalRequirements);
        Objects.requireNonNull(status, "Decision status must not be null");
        if (result == Result.CONDITIONAL_APPROVAL && conditionalRequirements == null) {
            throw new IllegalArgumentException(
                    "Conditional approval must contain conditional requirements");
        }
    }

    public enum DecisionType {
        PARTY_COMMITTEE_PRE_RESEARCH,
        MAJOR_DECISION,
        BOARD,
        MANAGEMENT
    }

    public enum Result {
        APPROVED,
        CONDITIONAL_APPROVAL,
        REJECTED,
        DEFERRED
    }

    public enum Status {
        DRAFT,
        RECORDED,
        CONDITIONS_PENDING,
        EFFECTIVE,
        VOIDED
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
