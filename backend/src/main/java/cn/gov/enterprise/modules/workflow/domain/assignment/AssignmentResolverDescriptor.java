package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.util.Objects;

/** Read-only registration metadata; it is not a dynamic configuration model. */
public record AssignmentResolverDescriptor(
        ResolverCode code, ResolverVersion version,
        AssignmentStrategy.Type strategyType, ResolverMode mode,
        ResolverContractHash contractHash, ResolverStatus status, boolean enabled) {

    public AssignmentResolverDescriptor {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(strategyType, "strategyType");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(contractHash, "contractHash");
        Objects.requireNonNull(status, "status");
    }
}
