package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;

/** Immutable ROLE_DIRECTORY_PORT_V1 query. */
public record RoleDirectoryQuery(
        String enterpriseId, String organizationId, String roleCode,
        Instant effectiveAt, String contractVersion, String traceId) {
    public static final String CONTRACT_VERSION = "ROLE_DIRECTORY_PORT_V1";

    public RoleDirectoryQuery {
        enterpriseId = required(enterpriseId, "enterpriseId", 100);
        organizationId = required(organizationId, "organizationId", 100);
        roleCode = stableCode(roleCode, "roleCode");
        if (effectiveAt == null) throw invalid("effectiveAt must not be null");
        contractVersion = required(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw invalid("unsupported directory contractVersion");
        }
        traceId = required(traceId, "traceId", 100);
    }

    static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw invalid(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw invalid(field + " is too long");
        return normalized;
    }

    static String stableCode(String value, String field) {
        String normalized = required(value, field, 100);
        if (!normalized.matches("[A-Z][A-Z0-9_]{2,99}")) {
            throw invalid(field + " must be an uppercase stable key");
        }
        return normalized;
    }

    private static RoleDirectoryException invalid(String message) {
        return new RoleDirectoryException(RoleDirectoryErrorCode.INVALID_DIRECTORY_QUERY, message);
    }
}
