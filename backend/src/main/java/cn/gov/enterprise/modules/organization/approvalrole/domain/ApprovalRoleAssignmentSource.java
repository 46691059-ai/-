package cn.gov.enterprise.modules.organization.approvalrole.domain;

import java.time.Instant;
import java.util.Objects;

public record ApprovalRoleAssignmentSource(ApprovalRoleAssignmentSourceType sourceType,
        String sourceSystem, String sourceReference, String sourceReferenceHash,
        int evidencePriority, Instant recordedAt) {
    public ApprovalRoleAssignmentSource {
        Objects.requireNonNull(sourceType, "sourceType");
        sourceSystem = ApprovalRole.required(sourceSystem, "sourceSystem", 100);
        sourceReference = ApprovalRole.required(sourceReference, "sourceReference", 200);
        if (sourceReferenceHash == null || !sourceReferenceHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceReferenceHash must be lowercase SHA-256");
        }
        if (evidencePriority < 0) throw new IllegalArgumentException("evidencePriority must not be negative");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
