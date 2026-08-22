package cn.gov.enterprise.modules.workflow.domain.role.persistence;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.time.Instant;
import java.util.Objects;

/** Immutable pre-instance candidate snapshot. It is not the V2.6.9 post-instance snapshot. */
public record RoleRuntimeBindingCandidateSnapshot(
        Long id, String snapshotId, RoleRuntimeBindingPromotionReference promotionReference,
        ResolverCode resolverCode, ResolverVersion resolverVersion,
        ResolverContractHash resolverContractHash, String bindingHash, String candidateHash,
        long directoryRevision, String businessScope, Instant effectiveFrom,
        Instant effectiveUntil, RoleRuntimeBindingEvidence evidence,
        String snapshotHash, String canonicalVersion, Instant createdAt,
        String createdBy, int version) {

    public RoleRuntimeBindingCandidateSnapshot {
        RoleRuntimeBindingPromotion.positive(id, "id");
        snapshotId = RoleRuntimeBindingPromotion.text(snapshotId, "snapshotId", 100);
        Objects.requireNonNull(promotionReference, "promotionReference");
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(resolverContractHash, "resolverContractHash");
        bindingHash = RoleRuntimeBindingPromotion.hash(bindingHash, "bindingHash");
        candidateHash = RoleRuntimeBindingPromotion.hash(candidateHash, "candidateHash");
        if (directoryRevision < 0) throw new IllegalArgumentException("directoryRevision must be non-negative");
        businessScope = RoleRuntimeBindingPromotion.text(businessScope, "businessScope", 200);
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(effectiveUntil, "effectiveUntil");
        if (!effectiveUntil.isAfter(effectiveFrom)) throw new IllegalArgumentException("invalid effective window");
        Objects.requireNonNull(evidence, "evidence");
        snapshotHash = RoleRuntimeBindingPromotion.hash(snapshotHash, "snapshotHash");
        canonicalVersion = RoleRuntimeBindingPromotion.text(
                canonicalVersion, "canonicalVersion", 64);
        if (!RoleRuntimeBindingPersistenceCanonical.VERSION.equals(canonicalVersion)) {
            throw new IllegalArgumentException("unsupported snapshot canonical version");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        createdBy = RoleRuntimeBindingPromotion.text(createdBy, "createdBy", 100);
        if (version != 0) throw new IllegalArgumentException("append-only snapshot version must be zero");
        String expected = RoleRuntimeBindingPersistenceCanonical.snapshotHash(
                snapshotId, promotionReference, resolverCode, resolverVersion,
                resolverContractHash, bindingHash, candidateHash, directoryRevision,
                businessScope, effectiveFrom, effectiveUntil, evidence.evidenceSetHash());
        if (!expected.equals(snapshotHash)) throw new IllegalArgumentException("snapshot hash mismatch");
    }
}
