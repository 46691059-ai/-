package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.util.Objects;

public record PersistentActivationRequest(
        Long id, String activationId, String resolverCode, String resolverVersion,
        String contractHash, String bindingHash, String candidateHash,
        String directoryContractHash, long directoryRevision, String activationHash,
        String approvalEvidenceHash, String businessScope, Instant effectiveAt,
        String requestedBy, PersistentActivationStatus status, int version) {

    public PersistentActivationRequest {
        activationId = PersistentActivationCanonical.text(activationId, "activationId", 100);
        resolverCode = PersistentActivationCanonical.text(resolverCode, "resolverCode", 64);
        resolverVersion = PersistentActivationCanonical.text(resolverVersion, "resolverVersion", 64);
        contractHash = PersistentActivationCanonical.hash(contractHash, "contractHash");
        bindingHash = PersistentActivationCanonical.hash(bindingHash, "bindingHash");
        candidateHash = PersistentActivationCanonical.hash(candidateHash, "candidateHash");
        directoryContractHash = PersistentActivationCanonical.hash(directoryContractHash, "directoryContractHash");
        activationHash = PersistentActivationCanonical.hash(activationHash, "activationHash");
        approvalEvidenceHash = PersistentActivationCanonical.hash(approvalEvidenceHash, "approvalEvidenceHash");
        if (directoryRevision < 0) throw new IllegalArgumentException("directoryRevision must be non-negative");
        businessScope = PersistentActivationCanonical.text(businessScope, "businessScope", 200);
        requestedBy = PersistentActivationCanonical.text(requestedBy, "requestedBy", 100);
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
    }

    public PersistentActivationRequest transitionTo(PersistentActivationStatus target) {
        boolean allowed = switch (status) {
            case DRAFT -> target == PersistentActivationStatus.SUBMITTED || terminal(target);
            case SUBMITTED -> target == PersistentActivationStatus.APPROVAL_PENDING || terminal(target);
            case APPROVAL_PENDING -> target == PersistentActivationStatus.APPROVED || terminal(target);
            case APPROVED -> target == PersistentActivationStatus.PERSISTED || terminal(target);
            case PERSISTED -> target == PersistentActivationStatus.REVOKED || target == PersistentActivationStatus.BLOCKED;
            case REJECTED, REVOKED, BLOCKED -> false;
        };
        if (!allowed) throw new IllegalStateException("illegal persistent activation transition: " + status + " -> " + target);
        return new PersistentActivationRequest(id, activationId, resolverCode, resolverVersion,
                contractHash, bindingHash, candidateHash, directoryContractHash,
                directoryRevision, activationHash, approvalEvidenceHash, businessScope,
                effectiveAt, requestedBy, target, version);
    }

    private static boolean terminal(PersistentActivationStatus value) {
        return value == PersistentActivationStatus.REJECTED
                || value == PersistentActivationStatus.REVOKED
                || value == PersistentActivationStatus.BLOCKED;
    }
}
