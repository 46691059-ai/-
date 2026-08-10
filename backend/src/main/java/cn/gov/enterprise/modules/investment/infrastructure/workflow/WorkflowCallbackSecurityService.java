package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class WorkflowCallbackSecurityService {
    private static final String NONCE_PREFIX = "enterprise:investment:workflow:nonce:";
    private final WorkflowProperties workflow;
    private final WorkflowCallbackSecurityProperties security;
    private final StringRedisTemplate redis;
    private final WorkflowReliabilityAuditService audit;

    public WorkflowCallbackSecurityService(
            WorkflowProperties workflow,
            WorkflowCallbackSecurityProperties security,
            StringRedisTemplate redis,
            WorkflowReliabilityAuditService audit) {
        this.workflow = workflow;
        this.security = security;
        this.redis = redis;
        this.audit = audit;
    }

    public String verify(byte[] body, CallbackHeaders headers, String method, String path) {
        try {
            verifyServiceToken(headers.authorization());
            String secret = resolveSecret(headers.keyId());
            Instant timestamp = parseTimestamp(headers.timestamp());
            if (Math.abs(Duration.between(timestamp, Instant.now()).toSeconds()) > security.allowedSkewSeconds()) {
                throw rejected("TIMESTAMP_OUT_OF_RANGE");
            }
            require(headers.nonce(), "NONCE_MISSING");
            require(headers.requestId(), "REQUEST_ID_MISSING");
            require(headers.contractVersion(), "CONTRACT_VERSION_MISSING");
            if (!"1.0".equals(headers.contractVersion())) throw rejected("CONTRACT_VERSION_UNSUPPORTED");

            String bodyHash = sha256(body);
            if (!constantEquals(bodyHash, headers.contentSha256())) throw rejected("CONTENT_HASH_MISMATCH");
            String canonical = method.toUpperCase() + '\n' + path + '\n' + bodyHash + '\n'
                    + headers.timestamp() + '\n' + headers.nonce() + '\n'
                    + headers.requestId() + '\n' + headers.contractVersion();
            String expected = hmac(secret, canonical);
            if (!constantEquals(expected, headers.signature())) throw rejected("SIGNATURE_INVALID");

            String nonceKey = NONCE_PREFIX + headers.keyId() + ':' + headers.nonce();
            boolean fresh;
            try {
                fresh = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                        nonceKey, headers.requestId(), Duration.ofSeconds(security.nonceTtlSeconds())));
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Workflow nonce store unavailable", exception);
            }
            if (!fresh) throw rejected("NONCE_REPLAYED");
            return bodyHash;
        } catch (BadCredentialsException exception) {
            audit.record(new WorkflowReliabilityAuditService.AuditCommand(
                    null, null, null, null, "SECURITY_REJECTED", "SYSTEM", "REJECTED",
                    exception.getMessage(), "requestId=" + safe(headers.requestId()),
                    null, null, null, safe(headers.traceId())));
            throw exception;
        } catch (RuntimeException exception) {
            audit.record(new WorkflowReliabilityAuditService.AuditCommand(
                    null, null, null, null, "SECURITY_REJECTED", "SYSTEM", "FAILED",
                    "SECURITY_DEPENDENCY_UNAVAILABLE", "requestId=" + safe(headers.requestId()),
                    null, null, null, safe(headers.traceId())));
            throw exception;
        }
    }

    private void verifyServiceToken(String authorization) {
        String token = workflow.callbackToken();
        if (token == null || token.isBlank()) throw rejected("SERVICE_CREDENTIAL_NOT_CONFIGURED");
        if (!constantEquals("Bearer " + token, authorization)) throw rejected("SERVICE_UNAUTHENTICATED");
    }

    private String resolveSecret(String keyId) {
        require(keyId, "KEY_ID_MISSING");
        if (keyId.equals(security.currentKeyId()) && hasText(security.currentSecret())) {
            return security.currentSecret();
        }
        if (keyId.equals(security.previousKeyId()) && hasText(security.previousSecret())) {
            return security.previousSecret();
        }
        throw rejected("KEY_ID_UNKNOWN");
    }

    private static Instant parseTimestamp(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw rejected("TIMESTAMP_INVALID");
        }
    }

    static String hmac(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot calculate Workflow signature", exception);
        }
    }

    static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean constantEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(String value, String code) {
        if (!hasText(value)) throw rejected(code);
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String safe(String value) { return value == null ? null : value.substring(0, Math.min(64, value.length())); }
    private static BadCredentialsException rejected(String code) { return new BadCredentialsException(code); }

    public record CallbackHeaders(
            String authorization, String keyId, String timestamp, String nonce,
            String requestId, String traceId, String contractVersion,
            String contentSha256, String signature) {}
}
