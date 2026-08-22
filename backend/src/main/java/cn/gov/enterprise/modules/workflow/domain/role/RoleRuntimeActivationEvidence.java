package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.util.Objects;

/** Complete immutable evidence presented to the fail-closed Activation Gate. */
public record RoleRuntimeActivationEvidence(
        String resolverContractHash,
        String bindingContractHash,
        String candidateContractHash,
        String directoryContractHash,
        String businessScope,
        Instant effectiveAt,
        boolean candidateRuleValid,
        boolean segregationOfDutiesAllowed,
        boolean auditComplete) {

    public RoleRuntimeActivationEvidence {
        resolverContractHash = RoleCandidateResult.hash(
                resolverContractHash, "resolverContractHash");
        bindingContractHash = RoleCandidateResult.hash(
                bindingContractHash, "bindingContractHash");
        candidateContractHash = RoleCandidateResult.hash(
                candidateContractHash, "candidateContractHash");
        directoryContractHash = RoleCandidateResult.hash(
                directoryContractHash, "directoryContractHash");
        businessScope = RoleDirectoryQuery.required(businessScope, "businessScope", 200);
        Objects.requireNonNull(effectiveAt, "effectiveAt");
    }
}
