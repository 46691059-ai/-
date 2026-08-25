package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable resolver manifest frozen for one Workflow instance. */
public record WorkflowResolverBindingSet(
        Long id, Long instanceId, Long definitionId, Long definitionVersionId,
        String manifestVersion, String bindingSetHash, int bindingCount,
        Status status, LocalDateTime frozenTime, String auditInfo, int version) {

    public enum Status { FROZEN, ARCHIVED, SECURITY_BLOCKED }

    public WorkflowResolverBindingSet {
        positive(id, "id"); positive(instanceId, "instanceId");
        positive(definitionId, "definitionId"); positive(definitionVersionId, "definitionVersionId");
        manifestVersion = required(manifestVersion, "manifestVersion", 64);
        sha256(bindingSetHash, "bindingSetHash");
        if (bindingCount <= 0) throw new IllegalArgumentException("bindingCount must be positive");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(frozenTime, "frozenTime");
        auditInfo = required(auditInfo, "auditInfo", 65535);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
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
