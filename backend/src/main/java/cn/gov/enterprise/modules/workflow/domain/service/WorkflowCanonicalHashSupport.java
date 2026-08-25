package cn.gov.enterprise.modules.workflow.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared deterministic UTF-8/SHA-256 and length-prefixed canonical encoding. */
public final class WorkflowCanonicalHashSupport {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private WorkflowCanonicalHashSupport() { }

    public static String canonical(Object... values) {
        Objects.requireNonNull(values, "values");
        StringBuilder result = new StringBuilder();
        for (Object value : values) append(result, value);
        return result.toString();
    }

    public static void append(StringBuilder target, Object value) {
        Objects.requireNonNull(target, "target");
        if (value == null) {
            target.append("-1:|");
            return;
        }
        String text = value instanceof Enum<?> item ? item.name() : value.toString();
        target.append(text.length()).append(':').append(text).append('|');
    }

    public static String sha256(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String requireSha256(String value, String field) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return value;
    }
}
