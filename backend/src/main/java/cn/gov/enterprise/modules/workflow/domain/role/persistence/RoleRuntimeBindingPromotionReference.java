package cn.gov.enterprise.modules.workflow.domain.role.persistence;

/** Stable source link from one Snapshot to one persisted Promotion. */
public record RoleRuntimeBindingPromotionReference(
        Long promotionRowId, String promotionId, String activationId,
        String activationHash, String promotionHash, String promotionEvidenceHash,
        String referenceHash) {

    public RoleRuntimeBindingPromotionReference {
        RoleRuntimeBindingPromotion.positive(promotionRowId, "promotionRowId");
        promotionId = RoleRuntimeBindingPromotion.text(promotionId, "promotionId", 100);
        activationId = RoleRuntimeBindingPromotion.text(activationId, "activationId", 100);
        activationHash = RoleRuntimeBindingPromotion.hash(activationHash, "activationHash");
        promotionHash = RoleRuntimeBindingPromotion.hash(promotionHash, "promotionHash");
        promotionEvidenceHash = RoleRuntimeBindingPromotion.hash(
                promotionEvidenceHash, "promotionEvidenceHash");
        referenceHash = RoleRuntimeBindingPromotion.hash(referenceHash, "referenceHash");
        String expected = RoleRuntimeBindingPersistenceCanonical.referenceHash(
                promotionId, activationId, activationHash, promotionHash, promotionEvidenceHash);
        if (!expected.equals(referenceHash)) throw new IllegalArgumentException("promotion reference hash mismatch");
    }
}
