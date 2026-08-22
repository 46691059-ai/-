package cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Canonical V1 implementation mirrored by the V2.6.16 database gates. */
public final class RoleRealtimeEligibilityPersistenceCanonical {
    public static final String ELIGIBILITY_VERSION = "ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1";
    public static final String PERSISTENCE_VERSION = "ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1";
    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private RoleRealtimeEligibilityPersistenceCanonical() { }

    public static String validatorHash(int order, String code, String status, String reason, Instant checkedAt) {
        return sha256("ROLE_REALTIME_VALIDATOR_EVIDENCE_V1" + field(order) + field(code)
                + field(status) + field(reason) + time(checkedAt));
    }

    public static String capabilityHash(String capability, String validator, String status,
            String decision, String provider, String policy, Instant checkedAt, Instant validUntil) {
        return sha256("ROLE_REALTIME_CAPABILITY_EVIDENCE_V1" + field(capability) + field(validator)
                + field(status) + field(decision) + field(provider) + field(policy)
                + time(checkedAt) + (validUntil == null ? "null" : time(validUntil)));
    }

    public static String validatorRoot(List<RoleRealtimeEligibilityPersistenceBundle.ValidatorEvidence> values) {
        return binaryRoot("ROLE_REALTIME_VALIDATOR_ROOT_V1", values.stream()
                .sorted(Comparator.comparingInt(RoleRealtimeEligibilityPersistenceBundle.ValidatorEvidence::order))
                .map(RoleRealtimeEligibilityPersistenceBundle.ValidatorEvidence::evidenceHash).toList());
    }

    public static String capabilityRoot(List<RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence> values) {
        return binaryRoot("ROLE_REALTIME_CAPABILITY_ROOT_V1", values.stream()
                .sorted(Comparator.comparing(RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence::capabilityCode))
                .map(RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence::evidenceHash).toList());
    }

    public static String persistenceHash(RoleRealtimeEligibilityPersistenceBundle.Header h) {
        return sha256(PERSISTENCE_VERSION + field(h.evidenceId()) + field(h.taskId())
                + field(h.instanceId()) + field(h.nodeExecutionId()) + field(h.candidatePoolId())
                + field(h.candidateMemberId()) + field(h.candidateUserId()) + h.eligibilityHash()
                + h.candidatePoolHash() + h.runtimeBindingHash() + field(h.roleCode())
                + field(h.organizationId()) + field(h.candidateDirectoryRevision())
                + field(h.claimDirectoryRevision()) + h.directoryResultHash() + h.validatorRootHash()
                + h.capabilityRootHash() + time(h.verifiedAt()) + time(h.expiresAt())
                + field(h.decision()) + field(h.policyVersion()));
    }

    public static String eventHash(RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent e) {
        return sha256("ROLE_REALTIME_ELIGIBILITY_EVENT_V1" + field(e.evidenceRowId())
                + field(e.sequenceNo()) + field(e.eventType())
                + (e.claimId() == null ? "null" : field(e.claimId())) + field(e.reasonCode())
                + (e.previousEventHash() == null ? "null" : e.previousEventHash())
                + time(e.occurredAt()) + field(e.operatorId()) + field(e.idempotencyKey()));
    }

    public static void verify(RoleRealtimeEligibilityPersistenceBundle b) {
        var h = b.header();
        if (b.validators().size() != 27 || b.capabilities().size() != 10
                || h.validatorCount() != 27 || h.capabilityCount() != 10
                || h.terminalValidatorOrder() != 27 || !"ELIGIBLE".equals(h.decision())) {
            throw new IllegalArgumentException("complete eligible evidence requires 27 validators and 10 capabilities");
        }
        for (var v : b.validators()) {
            if (!validatorHash(v.order(), v.code(), v.status(), v.reasonCode(), v.checkedAt()).equals(v.evidenceHash())) {
                throw new IllegalArgumentException("validator evidence hash mismatch");
            }
        }
        for (var c : b.capabilities()) {
            if (!capabilityHash(c.capabilityCode(), c.validatorCode(), c.status(), c.decision(),
                    c.providerVersion(), c.policyVersion(), c.checkedAt(), c.validUntil()).equals(c.evidenceHash())) {
                throw new IllegalArgumentException("capability evidence hash mismatch");
            }
        }
        if (!validatorRoot(b.validators()).equals(h.validatorRootHash())
                || !capabilityRoot(b.capabilities()).equals(h.capabilityRootHash())
                || !persistenceHash(h).equals(h.persistenceHash())
                || !eventHash(b.initialEvent()).equals(b.initialEvent().eventHash())) {
            throw new IllegalArgumentException("aggregate canonical hash mismatch");
        }
    }

    private static String field(Object value) { String s = String.valueOf(value); return s.length() + ":" + s; }
    private static String time(Instant value) { return SQL_TIME.format(value.truncatedTo(ChronoUnit.MILLIS)); }
    private static String sha256(String value) { return digest(value.getBytes(UTF_8)); }
    private static String binaryRoot(String prefix, List<String> hashes) {
        var bytes = new ByteArrayOutputStream();
        bytes.writeBytes(prefix.getBytes(UTF_8));
        hashes.forEach(hash -> bytes.writeBytes(HexFormat.of().parseHex(hash)));
        return digest(bytes.toByteArray());
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
