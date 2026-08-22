package cn.gov.enterprise.modules.workflow.domain.model;

import java.util.Objects;

/** Workflow definition aggregate root. Pure domain model without framework dependencies. */
public record WorkflowDefinition(
        Long id,
        String definitionCode,
        String definitionName,
        String businessType,
        Long enterpriseId,
        Long ownerOrgId,
        Status status,
        Long currentVersionId,
        String description,
        int version) {

    public enum Status { DRAFT, ACTIVE, INACTIVE, ARCHIVED }

    public WorkflowDefinition {
        Objects.requireNonNull(id, "id");
        definitionCode = required(definitionCode, "definitionCode", 100);
        definitionName = required(definitionName, "definitionName", 200);
        businessType = required(businessType, "businessType", 64);
        Objects.requireNonNull(enterpriseId, "enterpriseId");
        Objects.requireNonNull(status, "status");
        if (description != null && description.length() > 1000) {
            throw new IllegalArgumentException("description length must not exceed 1000");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    public static WorkflowDefinition draft(
            Long id, String code, String name, String businessType,
            Long enterpriseId, Long ownerOrgId, String description) {
        return new WorkflowDefinition(id, code, name, businessType, enterpriseId,
                ownerOrgId, Status.DRAFT, null, description, 0);
    }

    public WorkflowDefinition activateVersion(Long versionId) {
        Objects.requireNonNull(versionId, "versionId");
        if (status == Status.INACTIVE || status == Status.ARCHIVED) {
            throw new IllegalStateException("inactive or archived workflow definition cannot publish a version");
        }
        return new WorkflowDefinition(id, definitionCode, definitionName, businessType,
                enterpriseId, ownerOrgId, Status.ACTIVE, versionId, description, version + 1);
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " length must not exceed " + maxLength);
        }
        return normalized;
    }
}
