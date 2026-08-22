package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/** Creates a final append-only persistence package. It has no Runtime-enablement capability. */
public final class ActivationPersistencePolicy {

    public ActivationAuditTrail freezeApproved(
            String activationId, RoleRuntimeActivationSnapshot snapshot,
            long directoryRevision, Supplier<Long> idSupplier) {
        if (snapshot == null) throw new IllegalArgumentException("approved snapshot is required");
        if (!snapshot.activationHash().equals(snapshot.request().canonicalHash())) {
            throw new IllegalArgumentException("activation hash drift detected");
        }
        RoleRuntimeActivationRequest source = snapshot.request();
        PersistentActivationRequest request = new PersistentActivationRequest(
                idSupplier.get(), activationId, source.resolverCode().value(),
                source.resolverVersion().value(), source.contractHash().value(),
                source.bindingContractHash(), source.candidateContractHash(),
                source.directoryContractHash(), directoryRevision, snapshot.activationHash(),
                snapshot.evidenceHash(), source.businessScope(), source.effectiveAt(),
                source.requestedBy(), PersistentActivationStatus.PERSISTED, 0);

        List<PersistentActivationDecision> decisions = snapshot.decisions().stream()
                .map(item -> decision(idSupplier.get(), activationId, item, request)).toList();
        Long finalApprovalId = decisions.stream()
                .filter(item -> "RELEASE_APPROVER".equals(item.approverType()))
                .map(PersistentActivationDecision::id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("release approval is missing"));
        Instant createdAt = snapshot.approvedAt();
        List<ActivationEvidenceRecord> evidence = List.of(
                evidence(idSupplier.get(), finalApprovalId, request, ActivationEvidenceType.ACTIVATION_APPROVAL, snapshot.evidenceHash(), createdAt),
                evidence(idSupplier.get(), finalApprovalId, request, ActivationEvidenceType.RESOLVER_CONTRACT, source.contractHash().value(), createdAt),
                evidence(idSupplier.get(), finalApprovalId, request, ActivationEvidenceType.BINDING, source.bindingContractHash(), createdAt),
                evidence(idSupplier.get(), finalApprovalId, request, ActivationEvidenceType.CANDIDATE, source.candidateContractHash(), createdAt),
                evidence(idSupplier.get(), finalApprovalId, request, ActivationEvidenceType.DIRECTORY, source.directoryContractHash(), createdAt));
        String auditHash = PersistentActivationCanonical.auditHash(request, decisions, evidence);
        return new ActivationAuditTrail(request, decisions, evidence, auditHash);
    }

    private PersistentActivationDecision decision(
            Long id, String activationId, RoleRuntimeActivationDecision source,
            PersistentActivationRequest request) {
        String hash = PersistentActivationCanonical.decisionHash(activationId,
                source.approverRole().name(), source.approver(), source.decision().name(),
                source.timestamp(), source.evidenceHash());
        return new PersistentActivationDecision(id, activationId, source.approverRole().name(),
                source.approver(), source.decision().name(), source.reason(),
                source.evidenceHash(), hash, request.activationHash(), request.contractHash(),
                request.bindingHash(), source.timestamp(), 0);
    }

    private ActivationEvidenceRecord evidence(Long id, Long approvalId,
            PersistentActivationRequest request, ActivationEvidenceType type,
            String hash, Instant createdAt) {
        return new ActivationEvidenceRecord(id, approvalId, request.activationId(), type,
                hash, request.activationHash(), request.contractHash(), request.bindingHash(),
                PersistentActivationCanonical.VERSION, createdAt);
    }
}
