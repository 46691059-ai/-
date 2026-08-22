package cn.gov.enterprise.modules.workflow.domain.role.admission;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RoleRuntimeExecutionAdmissionEvidence(
        String admissionId,
        String admissionHash,
        String activationHash,
        String promotionHash,
        String bindingHash,
        String candidateHash,
        String resolverContractHash,
        long directoryRevision,
        String directoryResultHash,
        String definitionVersionId,
        String nodeId,
        List<RoleRuntimeExecutionAdmissionCheck> checks,
        Map<String, RoleRuntimeCapabilityResult> capabilityResults,
        RoleRuntimeExecutionAdmissionStatus status,
        Instant validatedAt) {

    public RoleRuntimeExecutionAdmissionEvidence {
        admissionId = RoleRuntimeExecutionAdmissionRequest.text(admissionId, "admissionId", 100);
        admissionHash = RoleRuntimeExecutionAdmissionRequest.hash(admissionHash, "admissionHash");
        activationHash = RoleRuntimeExecutionAdmissionRequest.hash(activationHash, "activationHash");
        promotionHash = RoleRuntimeExecutionAdmissionRequest.hash(promotionHash, "promotionHash");
        bindingHash = RoleRuntimeExecutionAdmissionRequest.hash(bindingHash, "bindingHash");
        candidateHash = RoleRuntimeExecutionAdmissionRequest.hash(candidateHash, "candidateHash");
        resolverContractHash = RoleRuntimeExecutionAdmissionRequest.hash(
                resolverContractHash, "resolverContractHash");
        if (directoryRevision < 0) {
            throw new IllegalArgumentException("directoryRevision must be non-negative");
        }
        directoryResultHash = RoleRuntimeExecutionAdmissionRequest.hash(
                directoryResultHash, "directoryResultHash");
        definitionVersionId = RoleRuntimeExecutionAdmissionRequest.text(
                definitionVersionId, "definitionVersionId", 100);
        nodeId = RoleRuntimeExecutionAdmissionRequest.text(nodeId, "nodeId", 100);
        checks = List.copyOf(checks);
        capabilityResults = Map.copyOf(capabilityResults);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validatedAt, "validatedAt");
    }
}
