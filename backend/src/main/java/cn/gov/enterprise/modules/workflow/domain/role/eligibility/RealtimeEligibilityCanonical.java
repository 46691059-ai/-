package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1: fixed field order, UTF-8, UTC epoch millis, lower SHA-256. */
public final class RealtimeEligibilityCanonical {
    public static final String VERSION = RealtimeEligibilityPolicy.CANONICAL_VERSION;

    private RealtimeEligibilityCanonical() { }

    public static String evidenceHash(
            RealtimeEligibilityQuery query,
            RealtimeEligibilityStatus status,
            Instant verifiedAt,
            Instant expiresAt,
            RealtimeUserStatusResult user,
            RealtimeCapabilityResult organization,
            RealtimeRoleMembershipResult role,
            RealtimeCapabilityResult dataScope,
            RealtimeCapabilityResult platformSoD,
            RealtimeCapabilityResult businessSoD,
            RealtimeCapabilityResult feature,
            RealtimeCapabilityResult canary,
            RealtimeCapabilityResult killSwitch,
            RealtimeCapabilityResult audit,
            List<RealtimeEligibilityValidationResult> validators) {
        StringBuilder value = new StringBuilder(2048);
        add(value, "canonicalVersion", VERSION);
        add(value, "enterpriseId", query.enterpriseId());
        add(value, "workflowInstanceId", query.workflowInstanceId());
        add(value, "taskId", query.taskId());
        add(value, "nodeExecutionId", query.nodeExecutionId());
        add(value, "candidatePoolId", query.candidatePoolId());
        add(value, "candidateUserId", query.candidateUserId());
        add(value, "roleCode", query.roleCode());
        add(value, "organizationId", query.organizationId());
        add(value, "claimAt", millis(query.claimAt()));
        add(value, "candidateDirectoryRevision", query.candidateDirectoryRevision());
        add(value, "candidateDirectoryResultHash", query.candidateDirectoryResultHash());
        add(value, "candidatePoolHash", query.candidatePoolHash());
        add(value, "runtimeBindingHash", query.runtimeBindingHash());
        add(value, "businessScopeReference", query.businessScopeReference());
        add(value, "status", status.name());
        add(value, "verifiedAt", millis(verifiedAt));
        add(value, "expiresAt", millis(expiresAt));
        add(value, "user", user == null ? null : user.status() + ":" + user.mappingVersion() + ":" + user.evidenceHash());
        add(value, "organization", capability(organization));
        add(value, "role", role(role));
        add(value, "dataScope", capability(dataScope));
        add(value, "platformSoD", capability(platformSoD));
        add(value, "businessSoD", capability(businessSoD));
        add(value, "feature", capability(feature));
        add(value, "canary", capability(canary));
        add(value, "killSwitch", capability(killSwitch));
        add(value, "audit", capability(audit));
        validators.stream().sorted(java.util.Comparator.comparingInt(RealtimeEligibilityValidationResult::order))
                .forEach(v -> add(value, "validator." + v.order(),
                        v.validatorCode() + ":" + v.status() + ":" + v.evidenceHash()));
        return sha256(value.toString());
    }

    public static String validatorHash(String code, String reason, Object fact) {
        StringBuilder value = new StringBuilder();
        add(value, "canonicalVersion", VERSION);
        add(value, "validatorCode", code);
        add(value, "reason", reason);
        add(value, "fact", fact);
        return sha256(value.toString());
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String capability(RealtimeCapabilityResult result) {
        return result == null ? null : result.outcome() + ":" + result.policyVersion() + ":" + result.evidenceHash();
    }

    private static String role(RealtimeRoleMembershipResult result) {
        return result == null ? null : result.outcome() + ":" + result.complete() + ":" + result.sourceConflict()
                + ":" + result.claimRevision() + ":" + result.directoryResultHash() + ":"
                + result.contractHash() + ":" + result.evidenceHash();
    }

    private static long millis(Instant value) { return value.toEpochMilli(); }

    private static void add(StringBuilder target, String key, Object value) {
        String text = value == null ? "null" : String.valueOf(value);
        target.append(key.length()).append(':').append(key).append('=')
                .append(text.length()).append(':').append(text).append(';');
    }
}
