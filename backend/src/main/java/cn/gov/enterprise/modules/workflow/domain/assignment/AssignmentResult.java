package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Successful, deterministic assignment resolution result. */
public record AssignmentResult(
        AssignmentStrategy.Type strategyType, AssignmentStrategy.Type targetType,
        String targetSnapshot, List<Long> resolvedUserIds,
        LocalDateTime resolveTime, String auditInfo) {

    public AssignmentResult {
        Objects.requireNonNull(strategyType, "strategyType");
        Objects.requireNonNull(targetType, "targetType");
        targetSnapshot = required(targetSnapshot, "targetSnapshot", 65535);
        resolveTime = Objects.requireNonNull(resolveTime, "resolveTime");
        auditInfo = required(auditInfo, "auditInfo", 65535);
        if (strategyType != targetType) {
            throw new IllegalArgumentException("strategyType and targetType must match in assignment V1");
        }
        if (resolvedUserIds == null || resolvedUserIds.isEmpty()) {
            throw new IllegalArgumentException("resolvedUserIds must not be empty");
        }
        List<Long> normalized = resolvedUserIds.stream()
                .peek(id -> {
                    if (id == null || id <= 0) throw new IllegalArgumentException("resolved user must be positive");
                })
                .distinct().sorted().toList();
        if (normalized.size() != resolvedUserIds.size()) {
            throw new IllegalArgumentException("resolvedUserIds must be unique");
        }
        resolvedUserIds = List.copyOf(normalized);
    }

    public Long singleUserId() {
        if (resolvedUserIds.size() != 1) {
            throw new IllegalStateException("explicit assignment requires exactly one resolved user");
        }
        return resolvedUserIds.getFirst();
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
