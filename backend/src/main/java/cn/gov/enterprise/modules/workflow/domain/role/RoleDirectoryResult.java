package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Atomic directory response with deterministic evidence hash. */
public record RoleDirectoryResult(
        String roleCode, String organizationId, Instant effectiveAt, long revision,
        boolean complete, List<RoleDirectoryMember> members, String resultHash,
        String contractHash, String source) {
    public RoleDirectoryResult {
        roleCode = RoleDirectoryQuery.stableCode(roleCode, "roleCode");
        organizationId = RoleDirectoryQuery.required(organizationId, "organizationId", 100);
        if (effectiveAt == null) throw new IllegalArgumentException("effectiveAt must not be null");
        if (revision <= 0) throw new IllegalArgumentException("revision must be positive");
        members = List.copyOf(members == null ? List.of() : members).stream()
                .sorted(Comparator.comparing(RoleDirectoryMember::userId)
                        .thenComparing(RoleDirectoryMember::assignmentId)).toList();
        resultHash = RoleDirectoryQuery.required(resultHash, "resultHash", 64);
        contractHash = RoleDirectoryQuery.required(contractHash, "contractHash", 64);
        source = RoleDirectoryQuery.required(source, "source", 100);
    }

    public static RoleDirectoryResult complete(
            String roleCode, String organizationId, Instant effectiveAt, long revision,
            List<RoleDirectoryMember> members, String contractHash, String source) {
        RoleDirectoryResult unhashed = new RoleDirectoryResult(roleCode, organizationId,
                effectiveAt, revision, true, members, "0".repeat(64), contractHash, source);
        return new RoleDirectoryResult(roleCode, organizationId, effectiveAt, revision, true,
                members, RoleDirectoryCanonical.resultHash(unhashed), contractHash, source);
    }

    public boolean hasValidHash() {
        return resultHash.equals(RoleDirectoryCanonical.resultHash(this));
    }
}
