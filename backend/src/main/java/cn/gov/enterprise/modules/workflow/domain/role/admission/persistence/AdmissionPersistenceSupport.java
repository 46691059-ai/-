package cn.gov.enterprise.modules.workflow.domain.role.admission.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

final class AdmissionPersistenceSupport {
    static final String CANONICAL = "ROLE_RUNTIME_EXECUTION_ADMISSION_PERSISTENCE_CANONICAL_V1";

    private AdmissionPersistenceSupport() { }

    static String text(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + max + " characters");
        }
        return value;
    }

    static String hash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lower-case SHA-256");
        }
        return value;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static String field(String name, Object value) {
        String text = value instanceof Instant instant ? instant.toString() : String.valueOf(value);
        return name.length() + ":" + name + text.length() + ":" + text;
    }
}
