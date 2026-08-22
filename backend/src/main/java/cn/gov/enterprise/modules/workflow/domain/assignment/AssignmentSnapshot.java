package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Immutable evidence of the assignment resolved when a task was created. */
public record AssignmentSnapshot(
        Long id, Long taskId, Long instanceId, Long versionId, Long nodeId, Long nodeExecutionId,
        AssignmentStrategy.Type strategyType, AssignmentStrategy.Type targetType,
        String targetSnapshot, List<Long> resolvedUserIds, LocalDateTime resolveTime,
        String auditInfo, String traceId, int version) {

    public AssignmentSnapshot {
        requirePositive(id, "id");
        requirePositive(taskId, "taskId");
        requirePositive(instanceId, "instanceId");
        requirePositive(versionId, "versionId");
        requirePositive(nodeId, "nodeId");
        requirePositive(nodeExecutionId, "nodeExecutionId");
        Objects.requireNonNull(strategyType, "strategyType");
        Objects.requireNonNull(targetType, "targetType");
        targetSnapshot = required(targetSnapshot, "targetSnapshot", 65535);
        List<Long> suppliedUsers = List.copyOf(
                Objects.requireNonNull(resolvedUserIds, "resolvedUserIds"));
        if (suppliedUsers.isEmpty()
                || suppliedUsers.stream().anyMatch(idValue -> idValue == null || idValue <= 0)) {
            throw new IllegalArgumentException("resolvedUserIds must contain positive users");
        }
        List<Long> normalizedUsers = suppliedUsers.stream().distinct().sorted().toList();
        if (normalizedUsers.size() != suppliedUsers.size()) {
            throw new IllegalArgumentException("resolvedUserIds must be unique");
        }
        resolvedUserIds = List.copyOf(normalizedUsers);
        resolveTime = Objects.requireNonNull(resolveTime, "resolveTime");
        auditInfo = required(auditInfo, "auditInfo", 65535);
        traceId = optional(traceId, 64);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    public static AssignmentSnapshot create(
            Long id, AssignmentContext context, AssignmentResult result) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(result, "result");
        if (!context.resolveTime().equals(result.resolveTime())) {
            throw new IllegalArgumentException("assignment result time must match context");
        }
        return new AssignmentSnapshot(id, context.taskId(), context.instanceId(), context.versionId(),
                context.nodeId(), context.nodeExecutionId(), result.strategyType(), result.targetType(),
                result.targetSnapshot(), result.resolvedUserIds(), result.resolveTime(),
                result.auditInfo(), context.traceId(), 0);
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
