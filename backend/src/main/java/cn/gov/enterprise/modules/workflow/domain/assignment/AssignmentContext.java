package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable runtime context used while materializing a task assignment. */
public record AssignmentContext(
        Long taskId, Long instanceId, Long versionId, Long nodeId, Long nodeExecutionId,
        Long enterpriseId, Long initiatorUserId, Long initiatorOrgId,
        String resolvedBy, LocalDateTime resolveTime, String traceId) {

    public AssignmentContext {
        requirePositive(taskId, "taskId");
        requirePositive(instanceId, "instanceId");
        requirePositive(versionId, "versionId");
        requirePositive(nodeId, "nodeId");
        requirePositive(nodeExecutionId, "nodeExecutionId");
        requirePositive(enterpriseId, "enterpriseId");
        requirePositive(initiatorUserId, "initiatorUserId");
        requirePositive(initiatorOrgId, "initiatorOrgId");
        resolvedBy = required(resolvedBy, "resolvedBy", 100);
        Objects.requireNonNull(resolveTime, "resolveTime");
        traceId = optional(traceId, 64);
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > max) throw new IllegalArgumentException("traceId is too long");
        return normalized;
    }
}
