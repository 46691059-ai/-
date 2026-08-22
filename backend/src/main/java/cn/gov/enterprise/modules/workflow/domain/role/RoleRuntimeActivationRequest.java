package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.time.Instant;
import java.util.Objects;

/** Immutable request to evaluate ROLE Runtime activation; it never enables execution. */
public record RoleRuntimeActivationRequest(
        ResolverCode resolverCode,
        ResolverVersion resolverVersion,
        ResolverContractHash contractHash,
        String bindingContractHash,
        String candidateContractHash,
        String directoryContractHash,
        Instant effectiveAt,
        String businessScope,
        String requestedBy) {

    public RoleRuntimeActivationRequest {
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(contractHash, "contractHash");
        bindingContractHash = RoleCandidateResult.hash(
                bindingContractHash, "bindingContractHash");
        candidateContractHash = RoleCandidateResult.hash(
                candidateContractHash, "candidateContractHash");
        directoryContractHash = RoleCandidateResult.hash(
                directoryContractHash, "directoryContractHash");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        businessScope = RoleDirectoryQuery.required(businessScope, "businessScope", 200);
        requestedBy = RoleDirectoryQuery.required(requestedBy, "requestedBy", 100);
    }

    public String canonicalHash() {
        return RoleRuntimeActivationCanonical.requestHash(this);
    }
}
