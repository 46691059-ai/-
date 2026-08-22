package cn.gov.enterprise.modules.workflow.domain.role.admission.persistence;

import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionStatus;
import java.time.Instant;
import java.util.Objects;

public record PersistentRoleRuntimeExecutionAdmission(
        Long id, String admissionId, String requestId, String idempotencyKey,
        Long candidateSnapshotRowId, String snapshotId, String promotionId, String activationId,
        String activationHash, String promotionHash, String bindingHash, String candidateHash,
        String executionAdmissionHash, String capabilityEvidenceRootHash, String persistenceHash,
        String resolverCode, String resolverVersion, String resolverContractHash,
        long directoryRevision, String directoryResultHash, String directoryFenceTokenHash,
        Instant directoryFenceExpiresAt, Instant directoryVerifiedAt,
        String enterpriseId, String businessScope, Long definitionReleaseId, Long definitionId,
        Long definitionVersionId, Long nodeId, String nodeBindingHash, String graphHash,
        RoleRuntimeExecutionAdmissionStatus decision, String policyVersion, Instant effectiveAt,
        Instant admissionExpiresAt, int executedCheckCount, int lastCheckSequence,
        String featureFlagEvidenceHash, String canaryEvidenceHash, String killSwitchEvidenceHash,
        String requestedBy, String decidedBy, Instant decidedAt) {

    public PersistentRoleRuntimeExecutionAdmission {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(candidateSnapshotRowId, "candidateSnapshotRowId");
        Objects.requireNonNull(definitionReleaseId, "definitionReleaseId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(definitionVersionId, "definitionVersionId");
        Objects.requireNonNull(nodeId, "nodeId");
        admissionId = AdmissionPersistenceSupport.text(admissionId, "admissionId", 100);
        requestId = AdmissionPersistenceSupport.text(requestId, "requestId", 100);
        idempotencyKey = AdmissionPersistenceSupport.text(idempotencyKey, "idempotencyKey", 100);
        snapshotId = AdmissionPersistenceSupport.text(snapshotId, "snapshotId", 100);
        promotionId = AdmissionPersistenceSupport.text(promotionId, "promotionId", 100);
        activationId = AdmissionPersistenceSupport.text(activationId, "activationId", 100);
        resolverCode = AdmissionPersistenceSupport.text(resolverCode, "resolverCode", 64);
        resolverVersion = AdmissionPersistenceSupport.text(resolverVersion, "resolverVersion", 64);
        enterpriseId = AdmissionPersistenceSupport.text(enterpriseId, "enterpriseId", 100);
        businessScope = AdmissionPersistenceSupport.text(businessScope, "businessScope", 200);
        policyVersion = AdmissionPersistenceSupport.text(policyVersion, "policyVersion", 64);
        requestedBy = AdmissionPersistenceSupport.text(requestedBy, "requestedBy", 100);
        decidedBy = AdmissionPersistenceSupport.text(decidedBy, "decidedBy", 100);
        for (var pair : new String[][] {{activationHash,"activationHash"},{promotionHash,"promotionHash"},
                {bindingHash,"bindingHash"},{candidateHash,"candidateHash"},
                {executionAdmissionHash,"executionAdmissionHash"},
                {capabilityEvidenceRootHash,"capabilityEvidenceRootHash"},{persistenceHash,"persistenceHash"},
                {resolverContractHash,"resolverContractHash"},{directoryResultHash,"directoryResultHash"},
                {directoryFenceTokenHash,"directoryFenceTokenHash"},{nodeBindingHash,"nodeBindingHash"},
                {graphHash,"graphHash"},{featureFlagEvidenceHash,"featureFlagEvidenceHash"},
                {canaryEvidenceHash,"canaryEvidenceHash"},{killSwitchEvidenceHash,"killSwitchEvidenceHash"}}) {
            AdmissionPersistenceSupport.hash(pair[0], pair[1]);
        }
        if (directoryRevision < 0 || executedCheckCount < 1 || executedCheckCount > 28
                || lastCheckSequence != executedCheckCount) {
            throw new IllegalArgumentException("invalid revision or evidence sequence");
        }
        if (!resolverCode.matches("[A-Z0-9_]+") || !resolverVersion.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("resolver identity must be upper-case ASCII");
        }
        if (decision == null || !(decision == RoleRuntimeExecutionAdmissionStatus.ELIGIBLE
                || decision == RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION
                || decision == RoleRuntimeExecutionAdmissionStatus.BLOCKED
                || decision == RoleRuntimeExecutionAdmissionStatus.REJECTED)) {
            throw new IllegalArgumentException("only stable admission decisions can be persisted");
        }
        Objects.requireNonNull(directoryFenceExpiresAt, "directoryFenceExpiresAt");
        Objects.requireNonNull(directoryVerifiedAt, "directoryVerifiedAt");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(admissionExpiresAt, "admissionExpiresAt");
        Objects.requireNonNull(decidedAt, "decidedAt");
        if (!directoryFenceExpiresAt.isAfter(directoryVerifiedAt)
                || !admissionExpiresAt.isAfter(effectiveAt)) {
            throw new IllegalArgumentException("invalid frozen evidence time window");
        }
    }
}
