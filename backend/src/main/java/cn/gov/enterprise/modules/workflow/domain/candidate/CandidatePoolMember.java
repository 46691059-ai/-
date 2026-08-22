package cn.gov.enterprise.modules.workflow.domain.candidate;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable Candidate eligibility evidence frozen with a Candidate Pool. */
public record CandidatePoolMember(
        Long id, Long poolId, Long taskId, Long instanceId, Long candidateUserId,
        AssignmentStrategy.Type sourceType, String sourceRefSnapshot,
        Long orgIdSnapshot, Long positionIdSnapshot, Long roleIdSnapshot,
        String eligibilitySnapshot, String eligibilityHash, int sortOrder,
        LocalDateTime generatedTime, CandidateMemberStatus status,
        String auditInfo, int version) {

    public CandidatePoolMember {
        positive(id, "id");
        positive(poolId, "poolId");
        positive(taskId, "taskId");
        positive(instanceId, "instanceId");
        positive(candidateUserId, "candidateUserId");
        Objects.requireNonNull(sourceType, "sourceType");
        if (sourceType == AssignmentStrategy.Type.USER) {
            throw new IllegalArgumentException("USER candidates must keep the DIRECT path");
        }
        sourceRefSnapshot = required(sourceRefSnapshot, "sourceRefSnapshot", 200);
        optionalPositive(orgIdSnapshot, "orgIdSnapshot");
        optionalPositive(positionIdSnapshot, "positionIdSnapshot");
        optionalPositive(roleIdSnapshot, "roleIdSnapshot");
        eligibilitySnapshot = required(eligibilitySnapshot, "eligibilitySnapshot", 65535);
        sha256(eligibilityHash, "eligibilityHash");
        if (sortOrder <= 0) throw new IllegalArgumentException("sortOrder must be positive");
        Objects.requireNonNull(generatedTime, "generatedTime");
        Objects.requireNonNull(status, "status");
        auditInfo = required(auditInfo, "auditInfo", 65535);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }

    private static void optionalPositive(Long value, String field) {
        if (value != null && value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }

    static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }

    static void sha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 value");
        }
    }
}
