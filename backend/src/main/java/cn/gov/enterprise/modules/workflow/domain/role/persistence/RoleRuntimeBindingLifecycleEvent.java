package cn.gov.enterprise.modules.workflow.domain.role.persistence;

import java.time.Instant;
import java.util.Objects;

/** Append-only lifecycle event with a deterministic previous-event hash chain. */
public record RoleRuntimeBindingLifecycleEvent(
        Long id, Long snapshotRowId, String snapshotId, String promotionId,
        String activationId, long sequenceNo,
        RoleRuntimeBindingLifecycleStatus fromStatus,
        RoleRuntimeBindingLifecycleStatus toStatus,
        String reasonCode, String sourceEvidenceHash, String previousEventHash,
        String eventHash, Instant occurredAt, String operatorId,
        String canonicalVersion, int version) {

    public RoleRuntimeBindingLifecycleEvent {
        RoleRuntimeBindingPromotion.positive(id, "id");
        RoleRuntimeBindingPromotion.positive(snapshotRowId, "snapshotRowId");
        snapshotId = RoleRuntimeBindingPromotion.text(snapshotId, "snapshotId", 100);
        promotionId = RoleRuntimeBindingPromotion.text(promotionId, "promotionId", 100);
        activationId = RoleRuntimeBindingPromotion.text(activationId, "activationId", 100);
        if (sequenceNo <= 0) throw new IllegalArgumentException("sequenceNo must be positive");
        Objects.requireNonNull(toStatus, "toStatus");
        reasonCode = RoleRuntimeBindingPromotion.text(reasonCode, "reasonCode", 100);
        sourceEvidenceHash = RoleRuntimeBindingPromotion.hash(
                sourceEvidenceHash, "sourceEvidenceHash");
        if (sequenceNo == 1) {
            if (fromStatus != null || previousEventHash != null
                    || toStatus != RoleRuntimeBindingLifecycleStatus.CREATED) {
                throw new IllegalArgumentException("first lifecycle event must create the snapshot");
            }
        } else {
            Objects.requireNonNull(fromStatus, "fromStatus");
            previousEventHash = RoleRuntimeBindingPromotion.hash(
                    previousEventHash, "previousEventHash");
            if (!allowed(fromStatus, toStatus)) throw new IllegalArgumentException("illegal lifecycle transition");
        }
        eventHash = RoleRuntimeBindingPromotion.hash(eventHash, "eventHash");
        Objects.requireNonNull(occurredAt, "occurredAt");
        operatorId = RoleRuntimeBindingPromotion.text(operatorId, "operatorId", 100);
        canonicalVersion = RoleRuntimeBindingPromotion.text(
                canonicalVersion, "canonicalVersion", 64);
        if (!RoleRuntimeBindingPersistenceCanonical.VERSION.equals(canonicalVersion)) {
            throw new IllegalArgumentException("unsupported lifecycle canonical version");
        }
        if (version != 0) throw new IllegalArgumentException("append-only event version must be zero");
        String expected = RoleRuntimeBindingPersistenceCanonical.eventHash(
                snapshotId, sequenceNo, fromStatus, toStatus, reasonCode,
                sourceEvidenceHash, previousEventHash, occurredAt, operatorId);
        if (!expected.equals(eventHash)) throw new IllegalArgumentException("lifecycle event hash mismatch");
    }

    public static boolean allowed(RoleRuntimeBindingLifecycleStatus source,
            RoleRuntimeBindingLifecycleStatus target) {
        return switch (source) {
            case CREATED -> target == RoleRuntimeBindingLifecycleStatus.VALIDATED
                    || target == RoleRuntimeBindingLifecycleStatus.BLOCKED;
            case VALIDATED -> target == RoleRuntimeBindingLifecycleStatus.ACTIVE
                    || target == RoleRuntimeBindingLifecycleStatus.BLOCKED;
            case ACTIVE -> target == RoleRuntimeBindingLifecycleStatus.REVOKED;
            case BLOCKED, REVOKED -> false;
        };
    }
}
