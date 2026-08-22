package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Frozen resolver output. WF3.6 permits exactly one explicit USER candidate. */
public record CandidatePool(
        AssignmentStrategy.Type strategyType, AssignmentStrategy.Type targetType,
        String targetSnapshot, List<CandidateUser> candidates,
        LocalDateTime generatedTime, LocalDateTime validUntil,
        String resolverVersion, String assignmentReason, String auditInfo) {

    public CandidatePool {
        Objects.requireNonNull(strategyType, "strategyType");
        Objects.requireNonNull(targetType, "targetType");
        if (strategyType != targetType) {
            throw new IllegalArgumentException("strategyType and targetType must match");
        }
        targetSnapshot = required(targetSnapshot, "targetSnapshot", 65535);
        List<CandidateUser> supplied = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (supplied.isEmpty()) throw new IllegalArgumentException("candidate pool must not be empty");
        List<CandidateUser> normalized = supplied.stream()
                .sorted(Comparator.comparingInt(CandidateUser::rank)
                        .thenComparing(CandidateUser::userId))
                .toList();
        long uniqueUsers = normalized.stream().map(CandidateUser::userId).distinct().count();
        if (uniqueUsers != normalized.size()) {
            throw new IllegalArgumentException("candidate users must be unique");
        }
        candidates = List.copyOf(normalized);
        Objects.requireNonNull(generatedTime, "generatedTime");
        if (validUntil != null && !validUntil.isAfter(generatedTime)) {
            throw new IllegalArgumentException("pool validUntil must be after generatedTime");
        }
        if (candidates.stream().anyMatch(candidate -> !candidate.generatedTime().equals(generatedTime)
                || !Objects.equals(candidate.validUntil(), validUntil))) {
            throw new IllegalArgumentException("candidate time window must match pool");
        }
        resolverVersion = required(resolverVersion, "resolverVersion", 100);
        assignmentReason = required(assignmentReason, "assignmentReason", 200);
        auditInfo = required(auditInfo, "auditInfo", 65535);
    }

    public Long singleUserId() {
        if (candidates.size() != 1) {
            throw new IllegalStateException("DIRECT assignment requires exactly one candidate");
        }
        return candidates.getFirst().userId();
    }

    public List<Long> candidateUserIds() {
        return candidates.stream().map(CandidateUser::userId).toList();
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
