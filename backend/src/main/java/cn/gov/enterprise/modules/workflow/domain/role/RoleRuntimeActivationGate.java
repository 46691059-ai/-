package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import java.util.Objects;

/** Fail-closed metadata Gate. It has no Task, Candidate Pool, Claim, or enable operation. */
public final class RoleRuntimeActivationGate {
    private final RuntimeEligibilityRegistry resolverRegistry;

    public RoleRuntimeActivationGate(RuntimeEligibilityRegistry resolverRegistry) {
        this.resolverRegistry = Objects.requireNonNull(resolverRegistry, "resolverRegistry");
    }

    public RoleRuntimeActivationGateResult evaluate(
            RoleRuntimeActivationRequest request,
            RoleRuntimeActivationEvidence evidence) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(evidence, "evidence");
        String activationHash = request.canonicalHash();
        ResolverEligibilityResult resolver = resolverRegistry.inspect(
                request.resolverCode(), request.resolverVersion(), request.contractHash());
        if (!resolver.ready()) return blocked(resolver.reason(), activationHash);
        if (!request.contractHash().equals(
                ResolverContractHash.of(evidence.resolverContractHash()))) {
            return blocked("RESOLVER_CONTRACT_HASH_MISMATCH", activationHash);
        }
        if (!request.bindingContractHash().equals(evidence.bindingContractHash())) {
            return blocked("BINDING_HASH_MISMATCH", activationHash);
        }
        if (!request.candidateContractHash().equals(evidence.candidateContractHash())
                || !evidence.candidateRuleValid()) {
            return blocked("CANDIDATE_RULE_MISMATCH", activationHash);
        }
        if (!request.directoryContractHash().equals(evidence.directoryContractHash())) {
            return blocked("DIRECTORY_CONTRACT_MISMATCH", activationHash);
        }
        if (!request.businessScope().equals(evidence.businessScope())
                || !request.effectiveAt().equals(evidence.effectiveAt())) {
            return blocked("ACTIVATION_SCOPE_MISMATCH", activationHash);
        }
        if (!evidence.segregationOfDutiesAllowed()) {
            return blocked("SEGREGATION_OF_DUTIES_BLOCKED", activationHash);
        }
        if (!evidence.auditComplete()) {
            return blocked("AUDIT_EVIDENCE_INCOMPLETE", activationHash);
        }
        return new RoleRuntimeActivationGateResult(RoleRuntimeActivationStatus.ELIGIBLE,
                "ACTIVATION_ELIGIBILITY_PASSED", activationHash);
    }

    private RoleRuntimeActivationGateResult blocked(String reason, String activationHash) {
        return new RoleRuntimeActivationGateResult(RoleRuntimeActivationStatus.BLOCKED,
                reason, activationHash);
    }
}
