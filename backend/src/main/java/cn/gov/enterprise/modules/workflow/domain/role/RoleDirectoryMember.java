package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.util.Objects;

/** One immutable approval-role membership fact. */
public record RoleDirectoryMember(
        String userId, String assignmentId, String roleCode, String organizationId,
        Instant effectiveFrom, Instant effectiveTo, RoleDirectorySourceType sourceType,
        String sourceRef, long revision) {
    public RoleDirectoryMember {
        userId = RoleDirectoryQuery.required(userId, "userId", 100);
        assignmentId = RoleDirectoryQuery.required(assignmentId, "assignmentId", 100);
        roleCode = RoleDirectoryQuery.stableCode(roleCode, "roleCode");
        organizationId = RoleDirectoryQuery.required(organizationId, "organizationId", 100);
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
        }
        Objects.requireNonNull(sourceType, "sourceType");
        sourceRef = RoleDirectoryQuery.required(sourceRef, "sourceRef", 200);
        if (revision <= 0) throw new IllegalArgumentException("revision must be positive");
    }

    public boolean effectiveAt(Instant instant) {
        return !instant.isBefore(effectiveFrom)
                && (effectiveTo == null || instant.isBefore(effectiveTo));
    }
}
