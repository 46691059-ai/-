package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

import java.net.URI;

public record RoleDirectoryEndpointDescriptor(
        String providerCode, RoleDirectoryEnvironmentIdentity environmentIdentity,
        String serviceIdentity, URI endpointIdentity, String contractVersion, String contractHash) {
    public RoleDirectoryEndpointDescriptor {
        providerCode = required(providerCode, "providerCode");
        if (environmentIdentity == null) throw new IllegalArgumentException("environmentIdentity is required");
        serviceIdentity = required(serviceIdentity, "serviceIdentity");
        if (endpointIdentity == null || endpointIdentity.getHost() == null) {
            throw new IllegalArgumentException("absolute endpointIdentity is required");
        }
        contractVersion = required(contractVersion, "contractVersion");
        contractHash = hash(contractHash, "contractHash");
    }

    static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    static String hash(String value, String field) {
        String normalized = required(value, field);
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        return normalized;
    }
}
