package cn.gov.enterprise.modules.organization.approvalrole.domain;

import java.time.Instant;
import java.util.Objects;

public record ApprovalRoleAssignment(long id, String enterpriseId, long organizationId,
        long roleId, ApprovalRoleCode roleCode, long userId, Instant effectiveFrom,
        Instant effectiveTo, ApprovalRoleAssignmentStatus status,
        ApprovalRoleAssignmentSource source, String assignmentKeyHash,
        String createdBy, Instant createdTime, String updatedBy, Instant updatedTime,
        int version, long deleteToken) {
    public ApprovalRoleAssignment {
        if (id <= 0 || organizationId <= 0 || roleId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("assignment identifiers must be positive");
        }
        enterpriseId = ApprovalRole.required(enterpriseId, "enterpriseId", 100);
        Objects.requireNonNull(roleCode, "roleCode");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        hash(assignmentKeyHash, "assignmentKeyHash");
        ApprovalRole.required(createdBy, "createdBy", 64);
        ApprovalRole.required(updatedBy, "updatedBy", 64);
        Objects.requireNonNull(createdTime, "createdTime");
        Objects.requireNonNull(updatedTime, "updatedTime");
        if (version < 0 || deleteToken < 0) throw new IllegalArgumentException("invalid governance version");
    }

    public boolean effectiveAt(Instant instant) {
        return status == ApprovalRoleAssignmentStatus.ACTIVE
                && !instant.isBefore(effectiveFrom)
                && (effectiveTo == null || instant.isBefore(effectiveTo));
    }

    public boolean overlaps(Instant from, Instant to) {
        return (effectiveTo == null || from.isBefore(effectiveTo))
                && (to == null || effectiveFrom.isBefore(to));
    }

    public ApprovalRoleAssignment end(Instant end, String actor, Instant now) {
        if (status != ApprovalRoleAssignmentStatus.ACTIVE) throw new IllegalStateException("assignment is not active");
        if (end == null || !end.isAfter(effectiveFrom)) throw new IllegalArgumentException("invalid assignment end");
        return new ApprovalRoleAssignment(id, enterpriseId, organizationId, roleId, roleCode,
                userId, effectiveFrom, end, ApprovalRoleAssignmentStatus.ENDED, source,
                assignmentKeyHash, createdBy, createdTime, actor, now, version + 1, deleteToken);
    }

    public ApprovalRoleAssignment corrected(String actor, Instant now) {
        if (status == ApprovalRoleAssignmentStatus.CORRECTED) throw new IllegalStateException("assignment already corrected");
        return new ApprovalRoleAssignment(id, enterpriseId, organizationId, roleId, roleCode,
                userId, effectiveFrom, effectiveTo, ApprovalRoleAssignmentStatus.CORRECTED,
                source, assignmentKeyHash, createdBy, createdTime, actor, now, version + 1, deleteToken);
    }

    static String hash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        return value;
    }
}
