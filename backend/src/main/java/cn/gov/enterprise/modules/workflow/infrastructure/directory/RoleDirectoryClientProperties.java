package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

/** Typed, secret-free endpoint configuration. No production endpoint is supplied by default. */
public record RoleDirectoryClientProperties(
        URI endpointIdentity, Duration connectTimeout, Duration readTimeout, int maxRetry,
        String environmentIdentity, String providerCode, String expectedContractVersion,
        String expectedContractHash) {
    private static final Set<String> ENVIRONMENTS = Set.of("development", "test", "staging", "production");

    public RoleDirectoryClientProperties {
        if (endpointIdentity == null || endpointIdentity.getScheme() == null
                || !(endpointIdentity.getScheme().equals("http") || endpointIdentity.getScheme().equals("https"))) {
            throw invalid("endpointIdentity must be an absolute HTTP(S) URI");
        }
        connectTimeout = duration(connectTimeout, "connectTimeout");
        readTimeout = duration(readTimeout, "readTimeout");
        if (maxRetry < 0 || maxRetry > 3) throw invalid("maxRetry must be between 0 and 3");
        environmentIdentity = text(environmentIdentity, "environmentIdentity");
        if (!ENVIRONMENTS.contains(environmentIdentity)) throw invalid("unsupported environmentIdentity");
        providerCode = stableCode(providerCode, "providerCode");
        expectedContractVersion = stableCode(expectedContractVersion, "expectedContractVersion");
        expectedContractHash = hash(expectedContractHash, "expectedContractHash");
    }

    public boolean production() { return "production".equals(environmentIdentity); }

    static String hash(String value, String field) {
        String normalized = text(value, field);
        if (!normalized.matches("[0-9a-f]{64}")) throw invalid(field + " must be lowercase SHA-256");
        return normalized;
    }

    static String text(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " must not be blank");
        return value.trim();
    }

    private static String stableCode(String value, String field) {
        String normalized = text(value, field);
        if (!normalized.matches("[A-Z][A-Z0-9_]{2,99}")) throw invalid(field + " must be an uppercase stable code");
        return normalized;
    }

    private static Duration duration(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofSeconds(30)) > 0) {
            throw invalid(field + " must be within (0, 30s]");
        }
        return value;
    }

    private static DirectoryFailure invalid(String message) {
        return new DirectoryFailure(DirectoryFailure.Code.INVALID_CONFIGURATION, message);
    }
}
