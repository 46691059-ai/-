package cn.gov.enterprise.modules.workflow.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable evidence of one accepted Workflow task action. */
public record WorkflowTaskAction(
        Long id, String actionNo, Long taskId, Long instanceId, ActionType actionType,
        Long operatorUserId, Long operatorOrgId, String actionComment, LocalDateTime actionTime,
        String idempotencyKey, String requestHash, String traceId, int version) {

    public enum ActionType { APPROVE, REJECT, WITHDRAW }

    public WorkflowTaskAction {
        Objects.requireNonNull(id, "id");
        actionNo = required(actionNo, "actionNo", 100);
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(operatorUserId, "operatorUserId");
        Objects.requireNonNull(operatorOrgId, "operatorOrgId");
        if (operatorUserId <= 0 || operatorOrgId <= 0) {
            throw new IllegalArgumentException("operator identity must be positive");
        }
        if (actionComment != null) {
            actionComment = actionComment.trim();
            if (actionComment.length() > 1000) throw new IllegalArgumentException("actionComment is too long");
        }
        Objects.requireNonNull(actionTime, "actionTime");
        idempotencyKey = required(idempotencyKey, "idempotencyKey", 200);
        requestHash = required(requestHash, "requestHash", 128);
        if (traceId != null && traceId.length() > 64) throw new IllegalArgumentException("traceId is too long");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    public static WorkflowTaskAction create(
            Long id, String actionNo, WorkflowTask task, ActionType actionType,
            Long operatorUserId, Long operatorOrgId, String actionComment,
            LocalDateTime actionTime, String idempotencyKey, String requestHash, String traceId) {
        return new WorkflowTaskAction(id, actionNo, task.id(), task.instanceId(), actionType,
                operatorUserId, operatorOrgId, actionComment, actionTime,
                idempotencyKey, requestHash, traceId, 0);
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
