package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.util.Objects;
import java.util.Set;

/** Immutable local Workflow facts loaded before remote capability evaluation. */
public record RealtimeEligibilityContext(
        RealtimeEligibilityQuery query,
        RealtimeEligibilityPolicy policy,
        LocalFacts facts,
        RealtimeEligibilityCapabilities.Bundle capabilities) {
    public RealtimeEligibilityContext {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(capabilities, "capabilities");
    }

    public record LocalFacts(
            boolean roleRuntimeGateOpen,
            boolean taskExists,
            boolean taskClaimable,
            boolean candidatePoolExists,
            long poolTaskId,
            Set<Long> frozenCandidateUserIds,
            boolean candidateMemberActive,
            String boundRuntimeBindingHash,
            String boundRoleCode,
            String boundOrganizationId,
            String boundEnterpriseId,
            boolean activeClaimExists,
            boolean taskAssigneeCompatible,
            boolean idempotencyCompatible,
            long taskVersion,
            String definitionVersion,
            String directoryContractHash,
            boolean directoryCircuitOpen) {
        public LocalFacts {
            frozenCandidateUserIds = Set.copyOf(Objects.requireNonNull(frozenCandidateUserIds, "frozenCandidateUserIds"));
            boundRuntimeBindingHash = RealtimeEligibilityQuery.hash(boundRuntimeBindingHash, "boundRuntimeBindingHash");
            boundRoleCode = RealtimeEligibilityQuery.upperCode(boundRoleCode, "boundRoleCode");
            boundOrganizationId = RealtimeEligibilityQuery.text(boundOrganizationId, "boundOrganizationId", 100);
            boundEnterpriseId = RealtimeEligibilityQuery.text(boundEnterpriseId, "boundEnterpriseId", 100);
            definitionVersion = RealtimeEligibilityQuery.text(definitionVersion, "definitionVersion", 100);
            directoryContractHash = RealtimeEligibilityQuery.hash(directoryContractHash, "directoryContractHash");
            if (poolTaskId <= 0 || taskVersion < 0) throw new IllegalArgumentException("invalid local fact identity/version");
        }
    }
}
