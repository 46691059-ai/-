package cn.gov.enterprise.modules.investment.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** Remediation condition created after a conditional Workflow approval. */
public record DecisionCondition(
        Long id, Long decisionId, Long snapshotId, Long sourceNodeId,
        String conditionNo, String content, boolean blocking,
        Long responsibleOrgId, Long responsiblePersonId, LocalDate deadline,
        String riskLevel, Status status, int version) {

    public DecisionCondition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId");
        Objects.requireNonNull(status, "status");
        if (conditionNo == null || conditionNo.isBlank()) throw new IllegalArgumentException("conditionNo is required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("condition content is required");
        if (deadline == null) throw new IllegalArgumentException("deadline is required");
    }

    public enum Status {
        OPEN, IN_PROGRESS, SUBMITTED, VERIFIED, CLOSED, REJECTED;

        public static Status fromStorage(String value) {
            return switch (value) {
                case "OPEN" -> OPEN;
                case "IN_PROGRESS" -> IN_PROGRESS;
                case "SUBMITTED" -> SUBMITTED;
                case "VERIFIED" -> VERIFIED;
                case "REJECTED", "EXPIRED" -> REJECTED;
                case "CLOSED", "WAIVED", "CANCELLED" -> CLOSED;
                default -> throw new IllegalArgumentException("Unknown decision condition status: " + value);
            };
        }
    }

    public DecisionCondition transitionTo(Status target) {
        boolean allowed = switch (status) {
            case OPEN, REJECTED -> target == Status.IN_PROGRESS || target == Status.SUBMITTED;
            case IN_PROGRESS -> target == Status.SUBMITTED;
            case SUBMITTED -> target == Status.VERIFIED || target == Status.REJECTED;
            case VERIFIED -> target == Status.CLOSED;
            case CLOSED -> false;
        };
        if (!allowed) throw new IllegalStateException("Illegal condition transition: " + status + " -> " + target);
        return new DecisionCondition(id, decisionId, snapshotId, sourceNodeId, conditionNo, content,
                blocking, responsibleOrgId, responsiblePersonId, deadline, riskLevel, target, version);
    }
}
