package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.Comparator;
import java.util.List;

/** One deduplicated user with every assignment fact retained as frozen evidence. */
public record RoleCandidateUser(
        String userId, CandidateSource source, List<RoleDirectoryMember> assignments) {
    public RoleCandidateUser {
        userId = RoleDirectoryQuery.required(userId, "userId", 100);
        String candidateUserId = userId;
        if (source == null) throw new IllegalArgumentException("source must not be null");
        assignments = List.copyOf(assignments).stream()
                .sorted(Comparator.comparing(RoleDirectoryMember::assignmentId)
                        .thenComparing(member -> member.sourceType().name())
                        .thenComparing(RoleDirectoryMember::sourceRef))
                .toList();
        if (assignments.isEmpty()) throw new IllegalArgumentException("assignments must not be empty");
        if (assignments.stream().anyMatch(member -> !member.userId().equals(candidateUserId))) {
            throw new IllegalArgumentException("assignment belongs to another candidate user");
        }
    }
}
