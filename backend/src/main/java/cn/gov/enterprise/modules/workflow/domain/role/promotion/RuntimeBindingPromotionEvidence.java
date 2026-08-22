package cn.gov.enterprise.modules.workflow.domain.role.promotion;

import cn.gov.enterprise.modules.workflow.domain.role.ActivationAuditTrail;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationCanonical;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationStatus;
import java.time.Instant;
import java.util.Objects;

/** Hash-only projection of the authoritative V2.6.13 activation evidence chain. */
public record RuntimeBindingPromotionEvidence(
        String activationId,
        PersistentActivationStatus activationStatus,
        String activationHash,
        String approvalEvidenceHash,
        String resolverCode,
        String resolverVersion,
        String resolverContractHash,
        String bindingHash,
        String candidateHash,
        String directoryContractHash,
        long directoryRevision,
        String businessScope,
        Instant effectiveAt,
        String auditHash,
        String promotionEvidenceHash) {

    public RuntimeBindingPromotionEvidence {
        activationId = PersistentActivationCanonical.text(activationId, "activationId", 100);
        Objects.requireNonNull(activationStatus, "activationStatus");
        activationHash = PersistentActivationCanonical.hash(activationHash, "activationHash");
        approvalEvidenceHash = PersistentActivationCanonical.hash(
                approvalEvidenceHash, "approvalEvidenceHash");
        resolverCode = PersistentActivationCanonical.text(resolverCode, "resolverCode", 64);
        resolverVersion = PersistentActivationCanonical.text(resolverVersion, "resolverVersion", 64);
        resolverContractHash = PersistentActivationCanonical.hash(
                resolverContractHash, "resolverContractHash");
        bindingHash = PersistentActivationCanonical.hash(bindingHash, "bindingHash");
        candidateHash = PersistentActivationCanonical.hash(candidateHash, "candidateHash");
        directoryContractHash = PersistentActivationCanonical.hash(
                directoryContractHash, "directoryContractHash");
        if (directoryRevision < 0) throw new IllegalArgumentException("directoryRevision must be non-negative");
        businessScope = PersistentActivationCanonical.text(businessScope, "businessScope", 200);
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        auditHash = PersistentActivationCanonical.hash(auditHash, "auditHash");
        promotionEvidenceHash = PersistentActivationCanonical.hash(
                promotionEvidenceHash, "promotionEvidenceHash");
        String expected = RuntimeBindingPromotionCanonical.evidenceHash(
                activationId, activationStatus.name(), activationHash, approvalEvidenceHash, auditHash,
                resolverCode, resolverVersion, resolverContractHash, bindingHash,
                candidateHash, directoryContractHash, directoryRevision, businessScope,
                effectiveAt.toString());
        if (!expected.equals(promotionEvidenceHash)) {
            throw new IllegalArgumentException("promotion evidence hash mismatch");
        }
    }

    public static RuntimeBindingPromotionEvidence from(ActivationAuditTrail trail) {
        Objects.requireNonNull(trail, "activation evidence");
        var request = trail.request();
        String evidenceHash = RuntimeBindingPromotionCanonical.evidenceHash(
                request.activationId(), request.status().name(), request.activationHash(),
                request.approvalEvidenceHash(),
                trail.auditHash(), request.resolverCode(), request.resolverVersion(),
                request.contractHash(), request.bindingHash(), request.candidateHash(),
                request.directoryContractHash(), request.directoryRevision(),
                request.businessScope(), request.effectiveAt().toString());
        return new RuntimeBindingPromotionEvidence(
                request.activationId(), request.status(), request.activationHash(),
                request.approvalEvidenceHash(), request.resolverCode(), request.resolverVersion(),
                request.contractHash(), request.bindingHash(), request.candidateHash(),
                request.directoryContractHash(), request.directoryRevision(),
                request.businessScope(), request.effectiveAt(), trail.auditHash(), evidenceHash);
    }
}
