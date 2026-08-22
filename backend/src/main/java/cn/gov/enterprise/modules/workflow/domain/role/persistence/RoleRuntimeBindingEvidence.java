package cn.gov.enterprise.modules.workflow.domain.role.persistence;

/** Minimum hash-only evidence set; no approval text or directory members. */
public record RoleRuntimeBindingEvidence(
        String activationAuditHash, String approvalEvidenceHash,
        String promotionEvidenceHash, String permissionEvidenceHash,
        String directoryContractHash, String evidenceSetHash) {

    public RoleRuntimeBindingEvidence {
        activationAuditHash = RoleRuntimeBindingPromotion.hash(
                activationAuditHash, "activationAuditHash");
        approvalEvidenceHash = RoleRuntimeBindingPromotion.hash(
                approvalEvidenceHash, "approvalEvidenceHash");
        promotionEvidenceHash = RoleRuntimeBindingPromotion.hash(
                promotionEvidenceHash, "promotionEvidenceHash");
        permissionEvidenceHash = RoleRuntimeBindingPromotion.hash(
                permissionEvidenceHash, "permissionEvidenceHash");
        directoryContractHash = RoleRuntimeBindingPromotion.hash(
                directoryContractHash, "directoryContractHash");
        evidenceSetHash = RoleRuntimeBindingPromotion.hash(evidenceSetHash, "evidenceSetHash");
        String expected = RoleRuntimeBindingPersistenceCanonical.evidenceSetHash(
                activationAuditHash, approvalEvidenceHash, promotionEvidenceHash,
                permissionEvidenceHash, directoryContractHash);
        if (!expected.equals(evidenceSetHash)) throw new IllegalArgumentException("evidence set hash mismatch");
    }
}
