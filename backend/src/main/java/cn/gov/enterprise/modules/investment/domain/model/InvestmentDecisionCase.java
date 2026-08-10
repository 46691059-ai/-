package cn.gov.enterprise.modules.investment.domain.model;

import java.util.Objects;

/** Workflow-driven investment decision aggregate state. */
public record InvestmentDecisionCase(
        Long id,
        Long investmentId,
        String decisionNo,
        String subject,
        Status status,
        Long currentSnapshotId,
        int version) {

    public InvestmentDecisionCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(investmentId, "investmentId");
        if (decisionNo == null || decisionNo.isBlank()) throw new IllegalArgumentException("decisionNo is required");
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("subject is required");
        Objects.requireNonNull(status, "status");
    }

    public enum Status {
        DRAFT, SUBMITTED, IN_APPROVAL, APPROVED, REJECTED, WITHDRAWN, ARCHIVED;

        public static Status fromStorage(String value) {
            return switch (value) {
                case "NOT_SUBMITTED", "DRAFT", "MATERIAL_REVIEW", "ROUTE_CONFIRMED", "RETURNED" -> DRAFT;
                case "SUBMITTING", "SUBMITTED" -> SUBMITTED;
                case "IN_WORKFLOW", "IN_DECISION", "IN_APPROVAL" -> IN_APPROVAL;
                case "CONDITION_PENDING", "CONDITIONAL_PENDING", "APPROVED", "COMPLETED" -> APPROVED;
                case "REJECTED", "DEFERRED" -> REJECTED;
                case "WITHDRAWN" -> WITHDRAWN;
                case "SUPERSEDED", "ARCHIVED" -> ARCHIVED;
                default -> throw new IllegalArgumentException("Unknown investment decision status: " + value);
            };
        }
    }

    public boolean submissionInProgress() {
        return status == Status.SUBMITTED || status == Status.IN_APPROVAL;
    }

    public InvestmentDecisionCase transitionTo(Status target) {
        if (target == status) return this;
        boolean allowed = switch (status) {
            case DRAFT -> target == Status.SUBMITTED;
            case SUBMITTED -> target == Status.IN_APPROVAL || target == Status.REJECTED || target == Status.WITHDRAWN;
            case IN_APPROVAL -> target == Status.APPROVED || target == Status.REJECTED || target == Status.WITHDRAWN;
            case WITHDRAWN -> target == Status.SUBMITTED;
            case APPROVED -> target == Status.ARCHIVED;
            case REJECTED, ARCHIVED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Illegal investment decision transition: " + status + " -> " + target);
        }
        return new InvestmentDecisionCase(id, investmentId, decisionNo, subject, target, currentSnapshotId, version);
    }
}
