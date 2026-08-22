package cn.gov.enterprise.modules.organization.approvalrole.domain;

import java.time.Instant;
import java.util.Objects;

public record ApprovalRoleRevision(long id, String enterpriseId, long organizationId,
        ApprovalRoleCode roleCode, long revision, String resultHash, String changeType,
        String changeReason, Instant effectiveFrom, Instant affectedFrom, Instant affectedTo,
        String correctionReference, Instant publishedAt, String publishedBy,
        Long previousRevision, String previousResultHash, Instant createdTime, long deleteToken) {
    public ApprovalRoleRevision {
        if (id <= 0 || organizationId <= 0 || revision <= 0) throw new IllegalArgumentException("invalid revision identity");
        enterpriseId = ApprovalRole.required(enterpriseId, "enterpriseId", 100);
        Objects.requireNonNull(roleCode, "roleCode");
        ApprovalRoleAssignment.hash(resultHash, "resultHash");
        changeType = ApprovalRole.required(changeType, "changeType", 32);
        changeReason = ApprovalRole.required(changeReason, "changeReason", 500);
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(publishedAt, "publishedAt");
        publishedBy = ApprovalRole.required(publishedBy, "publishedBy", 64);
        if (previousRevision != null && previousRevision != revision - 1) throw new IllegalArgumentException("previousRevision must be contiguous");
        if (previousRevision != null) ApprovalRoleAssignment.hash(previousResultHash, "previousResultHash");
        Objects.requireNonNull(createdTime, "createdTime");
        if (deleteToken != 0) throw new IllegalArgumentException("revision is append-only");
    }
}
