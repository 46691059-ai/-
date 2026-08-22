package cn.gov.enterprise.modules.workflow.domain.role.admission;

import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingLifecycleStatus;
import java.time.Instant;
import java.util.Objects;

/** Frozen source facts loaded before validation; no value is refreshed during admission. */
public record RoleRuntimeExecutionAdmissionFacts(
        boolean candidateExists,
        RoleRuntimeBindingLifecycleStatus candidateStatus,
        boolean promotionEvidenceComplete,
        boolean activationEvidenceComplete,
        boolean activationRevoked,
        boolean promotionRevoked,
        String resolverCode,
        String resolverVersion,
        String resolverContractHash,
        String activationHash,
        String promotionHash,
        String bindingHash,
        String candidateHash,
        long directoryRevision,
        String directoryResultHash,
        String businessScope,
        String definitionId,
        String definitionVersionId,
        String nodeId,
        Instant effectiveAt,
        Instant effectiveUntil) {

    public RoleRuntimeExecutionAdmissionFacts {
        Objects.requireNonNull(candidateStatus, "candidateStatus");
        resolverCode = RoleRuntimeExecutionAdmissionRequest.text(
                resolverCode, "resolverCode", 64);
        resolverVersion = RoleRuntimeExecutionAdmissionRequest.text(
                resolverVersion, "resolverVersion", 64);
        resolverContractHash = RoleRuntimeExecutionAdmissionRequest.hash(
                resolverContractHash, "resolverContractHash");
        activationHash = RoleRuntimeExecutionAdmissionRequest.hash(
                activationHash, "activationHash");
        promotionHash = RoleRuntimeExecutionAdmissionRequest.hash(
                promotionHash, "promotionHash");
        bindingHash = RoleRuntimeExecutionAdmissionRequest.hash(bindingHash, "bindingHash");
        candidateHash = RoleRuntimeExecutionAdmissionRequest.hash(candidateHash, "candidateHash");
        if (directoryRevision < 0) {
            throw new IllegalArgumentException("directoryRevision must be non-negative");
        }
        directoryResultHash = RoleRuntimeExecutionAdmissionRequest.hash(
                directoryResultHash, "directoryResultHash");
        businessScope = RoleRuntimeExecutionAdmissionRequest.text(
                businessScope, "businessScope", 200);
        definitionId = RoleRuntimeExecutionAdmissionRequest.text(
                definitionId, "definitionId", 100);
        definitionVersionId = RoleRuntimeExecutionAdmissionRequest.text(
                definitionVersionId, "definitionVersionId", 100);
        nodeId = RoleRuntimeExecutionAdmissionRequest.text(nodeId, "nodeId", 100);
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(effectiveUntil, "effectiveUntil");
        if (!effectiveUntil.isAfter(effectiveAt)) {
            throw new IllegalArgumentException("effective window must be increasing");
        }
    }
}
