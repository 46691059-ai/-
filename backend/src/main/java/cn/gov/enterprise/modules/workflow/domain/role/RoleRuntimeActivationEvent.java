package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.canary.CanaryScope;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Authoritative append-only fact for the independent ROLE Runtime lifecycle. */
public record RoleRuntimeActivationEvent(long id, String eventId, CanaryScope scope,
        String eventType, long sequence, long revision,
        RoleRuntimeActivationState previousState, RoleRuntimeActivationState resultingState,
        String authorizationId, String authorizationType, String authorizationCommit,
        String observationEvidenceCommit, String runtimeEnablementEvidenceCommit,
        String runtimeReleaseCommit, String runtimeReleaseTag, String directoryResultHash,
        String versionBindingHash, String manifestHash, String contentHash,
        String structuralFingerprint, String actorType, String actorId, Instant occurredAt) {
    public static final String EVENT_TYPE = "ROLE_RUNTIME_ACTIVATED";
    public static final String AUTHORIZATION_TYPE = "ACTIVATE_ROLE_RUNTIME";
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    public RoleRuntimeActivationEvent {
        if (id <= 0 || sequence != 1 || revision != 1) throw new IllegalArgumentException("invalid activation identity/revision");
        text(eventId, "eventId", 100); Objects.requireNonNull(scope, "scope");
        if (!EVENT_TYPE.equals(eventType) || previousState != RoleRuntimeActivationState.DISABLED
                || resultingState != RoleRuntimeActivationState.ACTIVATED) throw new IllegalArgumentException("invalid activation transition");
        text(authorizationId, "authorizationId", 100);
        if (!AUTHORIZATION_TYPE.equals(authorizationType)) throw new IllegalArgumentException("invalid authorization type");
        commit(authorizationCommit); commit(observationEvidenceCommit);
        commit(runtimeEnablementEvidenceCommit); commit(runtimeReleaseCommit);
        text(runtimeReleaseTag, "runtimeReleaseTag", 100);
        hash(directoryResultHash); hash(versionBindingHash); hash(manifestHash);
        hash(contentHash); hash(structuralFingerprint);
        text(actorType, "actorType", 32); text(actorId, "actorId", 100);
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static void commit(String value) { if (value == null || !COMMIT.matcher(value).matches()) throw new IllegalArgumentException("invalid commit"); }
    private static void hash(String value) { if (value == null || !HASH.matcher(value).matches()) throw new IllegalArgumentException("invalid hash"); }
    private static void text(String value, String name, int max) { if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(name + " is invalid"); }
}
