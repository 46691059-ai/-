package cn.gov.enterprise.modules.workflow.domain.role.persistence;

import cn.gov.enterprise.modules.workflow.domain.role.ActivationAuditTrail;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationStatus;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionCanonical;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionDecision;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Fail-closed Candidate-to-Snapshot integrity policy. */
public final class RoleRuntimeBindingPersistencePolicy {

    public RoleRuntimeBindingPersistenceBundle freeze(String snapshotId,
            ActivationAuditTrail activationTrail, RuntimeBindingPromotionDecision decision,
            String operatorId, Instant persistedAt, LongSupplier ids) {
        Objects.requireNonNull(activationTrail, "activationTrail");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(persistedAt, "persistedAt");
        Objects.requireNonNull(ids, "ids");
        var activation = activationTrail.request();
        if (activation.status() != PersistentActivationStatus.PERSISTED) {
            throw new IllegalStateException("activation evidence is not PERSISTED");
        }
        if (decision.status() != RuntimeBindingPromotionStatus.PROMOTED) {
            throw new IllegalStateException("promotion decision is not PROMOTED");
        }
        var candidate = decision.candidateOptional().orElseThrow(
                () -> new IllegalStateException("PROMOTED decision has no candidate"));
        if (candidate.runtimeEnabled()) throw new IllegalStateException("runtime-enabled candidate is forbidden");
        var request = decision.request();
        RuntimeBindingPromotionEvidence promotionEvidence =
                RuntimeBindingPromotionEvidence.from(activationTrail);
        String expectedPromotionHash = RuntimeBindingPromotionCanonical.promotionHash(
                request, promotionEvidence);
        if (!expectedPromotionHash.equals(candidate.promotionHash())) {
            throw new IllegalStateException("promotion hash drift");
        }
        if (!activation.activationId().equals(candidate.activationId())
                || !activation.activationHash().equals(candidate.activationHash())) {
            throw new IllegalStateException("activation ownership or hash drift");
        }
        if (!activation.resolverCode().equals(candidate.resolverCode().value())
                || !activation.resolverVersion().equals(candidate.resolverVersion().value())
                || !activation.contractHash().equals(candidate.resolverContractHash().value())) {
            throw new IllegalStateException("resolver contract drift");
        }
        if (!activation.bindingHash().equals(candidate.bindingHash())) {
            throw new IllegalStateException("binding hash drift");
        }
        if (!activation.candidateHash().equals(candidate.candidateHash())) {
            throw new IllegalStateException("candidate hash drift");
        }
        if (activation.directoryRevision() != candidate.directoryRevision()) {
            throw new IllegalStateException("directory revision drift");
        }
        if (!activation.businessScope().equals(candidate.businessScope())
                || !activation.effectiveAt().equals(candidate.effectiveFrom())
                || persistedAt.isBefore(candidate.effectiveFrom())
                || !persistedAt.isBefore(candidate.effectiveUntil())) {
            throw new IllegalStateException("effective scope is invalid");
        }

        RoleRuntimeBindingPromotion promotion = new RoleRuntimeBindingPromotion(ids.getAsLong(),
                candidate.promotionId(), candidate.activationId(), candidate.activationHash(),
                activationTrail.auditHash(), activation.approvalEvidenceHash(),
                candidate.resolverCode(), candidate.resolverVersion(),
                candidate.resolverContractHash(), candidate.bindingHash(), candidate.candidateHash(),
                activation.directoryContractHash(), candidate.directoryRevision(),
                candidate.businessScope(), candidate.effectiveFrom(), candidate.effectiveUntil(),
                request.permissionEvidenceHash(), candidate.promotionEvidenceHash(),
                candidate.promotionHash(), candidate.promotedAt(), request.requestedBy(), 0);
        String referenceHash = RoleRuntimeBindingPersistenceCanonical.referenceHash(
                promotion.promotionId(), promotion.activationId(), promotion.activationHash(),
                promotion.promotionHash(), promotion.promotionEvidenceHash());
        RoleRuntimeBindingPromotionReference reference = new RoleRuntimeBindingPromotionReference(
                promotion.id(), promotion.promotionId(), promotion.activationId(),
                promotion.activationHash(), promotion.promotionHash(),
                promotion.promotionEvidenceHash(), referenceHash);
        String evidenceSetHash = RoleRuntimeBindingPersistenceCanonical.evidenceSetHash(
                promotion.activationAuditHash(), promotion.approvalEvidenceHash(),
                promotion.promotionEvidenceHash(), promotion.permissionEvidenceHash(),
                promotion.directoryContractHash());
        RoleRuntimeBindingEvidence evidence = new RoleRuntimeBindingEvidence(
                promotion.activationAuditHash(), promotion.approvalEvidenceHash(),
                promotion.promotionEvidenceHash(), promotion.permissionEvidenceHash(),
                promotion.directoryContractHash(), evidenceSetHash);
        String snapshotHash = RoleRuntimeBindingPersistenceCanonical.snapshotHash(
                snapshotId, reference, promotion.resolverCode(), promotion.resolverVersion(),
                promotion.resolverContractHash(), promotion.bindingHash(), promotion.candidateHash(),
                promotion.directoryRevision(), promotion.businessScope(), promotion.effectiveFrom(),
                promotion.effectiveUntil(), evidence.evidenceSetHash());
        RoleRuntimeBindingCandidateSnapshot snapshot = new RoleRuntimeBindingCandidateSnapshot(
                ids.getAsLong(), snapshotId, reference, promotion.resolverCode(),
                promotion.resolverVersion(), promotion.resolverContractHash(),
                promotion.bindingHash(), promotion.candidateHash(), promotion.directoryRevision(),
                promotion.businessScope(), promotion.effectiveFrom(), promotion.effectiveUntil(),
                evidence, snapshotHash, RoleRuntimeBindingPersistenceCanonical.VERSION,
                persistedAt, operatorId, 0);
        RoleRuntimeBindingLifecycleEvent created = firstEvent(ids.getAsLong(), snapshot,
                "SNAPSHOT_CREATED", persistedAt, operatorId);
        RoleRuntimeBindingLifecycleEvent validated = nextEvent(ids.getAsLong(), created,
                RoleRuntimeBindingLifecycleStatus.VALIDATED, "INTEGRITY_VALIDATED",
                snapshot.snapshotHash(), persistedAt, operatorId);
        return new RoleRuntimeBindingPersistenceBundle(
                promotion, snapshot, evidence, java.util.List.of(created, validated));
    }

    public RoleRuntimeBindingLifecycleEvent firstEvent(Long id,
            RoleRuntimeBindingCandidateSnapshot snapshot, String reason,
            Instant occurredAt, String operatorId) {
        String hash = RoleRuntimeBindingPersistenceCanonical.eventHash(snapshot.snapshotId(), 1,
                null, RoleRuntimeBindingLifecycleStatus.CREATED, reason,
                snapshot.snapshotHash(), null, occurredAt, operatorId);
        return new RoleRuntimeBindingLifecycleEvent(id, snapshot.id(), snapshot.snapshotId(),
                snapshot.promotionReference().promotionId(),
                snapshot.promotionReference().activationId(), 1, null,
                RoleRuntimeBindingLifecycleStatus.CREATED, reason, snapshot.snapshotHash(),
                null, hash, occurredAt, operatorId,
                RoleRuntimeBindingPersistenceCanonical.VERSION, 0);
    }

    public RoleRuntimeBindingLifecycleEvent nextEvent(Long id,
            RoleRuntimeBindingLifecycleEvent previous,
            RoleRuntimeBindingLifecycleStatus target, String reason,
            String sourceEvidenceHash, Instant occurredAt, String operatorId) {
        Objects.requireNonNull(previous, "previous");
        if (!RoleRuntimeBindingLifecycleEvent.allowed(previous.toStatus(), target)) {
            throw new IllegalStateException("illegal lifecycle transition");
        }
        long sequence = previous.sequenceNo() + 1;
        String hash = RoleRuntimeBindingPersistenceCanonical.eventHash(previous.snapshotId(),
                sequence, previous.toStatus(), target, reason, sourceEvidenceHash,
                previous.eventHash(), occurredAt, operatorId);
        return new RoleRuntimeBindingLifecycleEvent(id, previous.snapshotRowId(),
                previous.snapshotId(), previous.promotionId(), previous.activationId(), sequence,
                previous.toStatus(), target, reason, sourceEvidenceHash, previous.eventHash(),
                hash, occurredAt, operatorId,
                RoleRuntimeBindingPersistenceCanonical.VERSION, 0);
    }
}
