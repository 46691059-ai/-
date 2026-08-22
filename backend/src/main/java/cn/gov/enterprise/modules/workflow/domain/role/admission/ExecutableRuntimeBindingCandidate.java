package cn.gov.enterprise.modules.workflow.domain.role.admission;

import java.time.Instant;
import java.util.Objects;

/** Admission output only; it is not a resolver binding or any workflow runtime object. */
public record ExecutableRuntimeBindingCandidate(
        String admissionId, String admissionHash,
        String runtimeBindingCandidateId,
        String activationHash, String promotionHash, String bindingHash, String candidateHash,
        String resolverCode, String resolverVersion, String resolverContractHash,
        long directoryRevision, String directoryResultHash,
        String enterpriseId, String businessScope,
        String definitionId, String definitionVersionId, String nodeId,
        Instant effectiveAt, RoleRuntimeCanaryScope canaryScope,
        boolean runtimeEnabled) {

    public ExecutableRuntimeBindingCandidate {
        admissionId = RoleRuntimeExecutionAdmissionRequest.text(admissionId, "admissionId", 100);
        admissionHash = RoleRuntimeExecutionAdmissionRequest.hash(admissionHash, "admissionHash");
        runtimeBindingCandidateId = RoleRuntimeExecutionAdmissionRequest.text(
                runtimeBindingCandidateId, "runtimeBindingCandidateId", 100);
        activationHash = RoleRuntimeExecutionAdmissionRequest.hash(activationHash, "activationHash");
        promotionHash = RoleRuntimeExecutionAdmissionRequest.hash(promotionHash, "promotionHash");
        bindingHash = RoleRuntimeExecutionAdmissionRequest.hash(bindingHash, "bindingHash");
        candidateHash = RoleRuntimeExecutionAdmissionRequest.hash(candidateHash, "candidateHash");
        resolverCode = RoleRuntimeExecutionAdmissionRequest.resolver(resolverCode, "resolverCode");
        resolverVersion = RoleRuntimeExecutionAdmissionRequest.resolver(
                resolverVersion, "resolverVersion");
        resolverContractHash = RoleRuntimeExecutionAdmissionRequest.hash(
                resolverContractHash, "resolverContractHash");
        if (directoryRevision < 0) {
            throw new IllegalArgumentException("directoryRevision must be non-negative");
        }
        directoryResultHash = RoleRuntimeExecutionAdmissionRequest.hash(
                directoryResultHash, "directoryResultHash");
        enterpriseId = RoleRuntimeExecutionAdmissionRequest.text(enterpriseId, "enterpriseId", 100);
        businessScope = RoleRuntimeExecutionAdmissionRequest.text(
                businessScope, "businessScope", 200);
        definitionId = RoleRuntimeExecutionAdmissionRequest.text(definitionId, "definitionId", 100);
        definitionVersionId = RoleRuntimeExecutionAdmissionRequest.text(
                definitionVersionId, "definitionVersionId", 100);
        nodeId = RoleRuntimeExecutionAdmissionRequest.text(nodeId, "nodeId", 100);
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(canaryScope, "canaryScope");
        if (runtimeEnabled) {
            throw new IllegalArgumentException("execution admission must not enable ROLE runtime");
        }
    }

    static ExecutableRuntimeBindingCandidate from(
            RoleRuntimeExecutionAdmissionRequest request, String admissionHash) {
        return new ExecutableRuntimeBindingCandidate(
                request.requestId(), admissionHash, request.runtimeBindingCandidateId(),
                request.activationHash(), request.promotionHash(), request.bindingHash(),
                request.candidateHash(), request.resolverCode(), request.resolverVersion(),
                request.resolverContractHash(), request.directoryRevision(),
                request.directoryResultHash(), request.enterpriseId(), request.businessScope(),
                request.definitionId(), request.definitionVersionId(), request.nodeId(),
                request.effectiveAt(), request.canaryScope(), false);
    }
}
