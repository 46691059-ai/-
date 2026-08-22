package cn.gov.enterprise.modules.organization.approvalrole.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record ApprovalRoleDirectoryMember(long userId, List<Evidence> evidence) {
    public ApprovalRoleDirectoryMember {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        evidence = List.copyOf(evidence == null ? List.of() : evidence).stream()
                .sorted(Comparator.comparing(Evidence::assignmentId)).toList();
        if (evidence.isEmpty()) throw new IllegalArgumentException("member evidence must not be empty");
    }

    public record Evidence(long assignmentId, Instant effectiveFrom, Instant effectiveTo,
            ApprovalRoleAssignmentSource source) {
        public Evidence {
            if (assignmentId <= 0) throw new IllegalArgumentException("assignmentId must be positive");
            if (effectiveFrom == null || (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom))) {
                throw new IllegalArgumentException("invalid evidence interval");
            }
            if (source == null) throw new IllegalArgumentException("source must not be null");
        }
    }
}
