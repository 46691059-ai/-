package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.util.Objects;
import java.util.Set;

/** Read-only governance registry. READY here never means executable by ResolverRegistry. */
public final class RuntimeEligibilityRegistry {
    private final ResolverRegistry resolverRegistry;
    private final Set<String> disabledChecks;

    public RuntimeEligibilityRegistry(
            ResolverRegistry resolverRegistry, Set<String> disabledResolverCodes) {
        this.resolverRegistry = Objects.requireNonNull(resolverRegistry, "resolverRegistry");
        this.disabledChecks = Set.copyOf(Objects.requireNonNull(
                disabledResolverCodes, "disabledResolverCodes"));
    }

    public ResolverEligibilityResult inspect(
            ResolverCode code, ResolverVersion version, ResolverContractHash contractHash) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(contractHash, "contractHash");
        if (disabledChecks.contains(code.value())) {
            return blocked(null, "ELIGIBILITY_CHECK_DISABLED");
        }
        final AssignmentResolverDescriptor descriptor;
        try {
            descriptor = resolverRegistry.requireDescriptor(code, version);
        } catch (RuntimeException exception) {
            return blocked(null, "RESOLVER_NOT_FOUND");
        }
        if (descriptor.status() != ResolverStatus.PREPARED
                && descriptor.status() != ResolverStatus.ACTIVE) {
            return blocked(descriptor, "RESOLVER_STATUS_ILLEGAL");
        }
        if (!descriptor.contractHash().equals(contractHash)) {
            return blocked(descriptor, "RESOLVER_CONTRACT_HASH_MISMATCH");
        }
        return new ResolverEligibilityResult(RoleRuntimeEligibility.Status.READY,
                descriptor, "RESOLVER_METADATA_ELIGIBLE");
    }

    private ResolverEligibilityResult blocked(
            AssignmentResolverDescriptor descriptor, String reason) {
        return new ResolverEligibilityResult(RoleRuntimeEligibility.Status.BLOCKED,
                descriptor, reason);
    }
}
