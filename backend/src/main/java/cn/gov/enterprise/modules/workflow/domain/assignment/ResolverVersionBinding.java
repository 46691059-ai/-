package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.util.Objects;

/** Resolver identity frozen for the lifetime of one Workflow Instance. */
public record ResolverVersionBinding(
        Long instanceId, ResolverCode resolverCode, ResolverVersion resolverVersion,
        ResolverContractHash contractHash) {

    public ResolverVersionBinding {
        if (instanceId == null || instanceId <= 0) {
            throw new IllegalArgumentException("binding instanceId must be positive");
        }
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(contractHash, "contractHash");
    }

    public static ResolverVersionBinding freeze(
            Long instanceId, AssignmentResolverDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.status() != ResolverStatus.ACTIVE || !descriptor.enabled()) {
            throw new IllegalStateException("only an active enabled resolver can be frozen");
        }
        return new ResolverVersionBinding(instanceId, descriptor.code(), descriptor.version(),
                descriptor.contractHash());
    }
}
