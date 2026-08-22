package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable, non-sensitive candidate evidence produced by a resolver. */
public record CandidateUser(
        Long userId, AssignmentStrategy.Type sourceType, String sourceReference,
        int rank, LocalDateTime generatedTime, LocalDateTime validUntil) {

    public CandidateUser {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("candidate userId must be positive");
        }
        Objects.requireNonNull(sourceType, "sourceType");
        sourceReference = required(sourceReference, "sourceReference", 200);
        if (rank <= 0) throw new IllegalArgumentException("candidate rank must be positive");
        Objects.requireNonNull(generatedTime, "generatedTime");
        if (validUntil != null && !validUntil.isAfter(generatedTime)) {
            throw new IllegalArgumentException("candidate validUntil must be after generatedTime");
        }
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
