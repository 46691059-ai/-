package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** SHA-256 identity of externally observable resolver behavior. */
public record ResolverContractHash(String value) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ResolverContractHash {
        if (value == null || !SHA_256.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("resolver contract hash must be lowercase SHA-256");
        }
        value = value.trim();
    }

    public static ResolverContractHash of(String value) {
        return new ResolverContractHash(value);
    }

    public static ResolverContractHash sha256(String canonicalContract) {
        if (canonicalContract == null || canonicalContract.isBlank()) {
            throw new IllegalArgumentException("canonical resolver contract must not be blank");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalContract.getBytes(StandardCharsets.UTF_8));
            return new ResolverContractHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
