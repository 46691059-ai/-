package cn.gov.enterprise.modules.workflow.domain.binding;

import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCanonicalHashSupport;
import java.util.Objects;

/** VERSION_NODE_RESOLVER_BINDING_V1 deterministic canonical contract. */
public final class VersionNodeResolverBindingCanonical {
    private VersionNodeResolverBindingCanonical() { }

    public static VersionNodeResolverBindingComputation compute(
            VersionNodeResolverBinding binding) {
        String canonical = canonical(binding);
        return new VersionNodeResolverBindingComputation(
                binding, VersionNodeResolverBinding.SCHEMA_VERSION,
                canonical, WorkflowCanonicalHashSupport.sha256(canonical));
    }

    public static String canonical(VersionNodeResolverBinding binding) {
        Objects.requireNonNull(binding, "binding");
        return WorkflowCanonicalHashSupport.canonical(
                binding.bindingSchemaVersion(),
                binding.definitionId(),
                binding.definitionVersionId(),
                binding.nodeId(),
                binding.bindingOrder(),
                binding.resolverCode().value(),
                binding.resolverVersion().value(),
                binding.resolverContractHash().value(),
                binding.strategyType(),
                binding.resolverMode(),
                binding.targetType(),
                binding.roleCode(),
                binding.organizationScopeType(),
                binding.organizationId(),
                binding.effectiveTimePolicy());
    }

    static String logicalFingerprint(VersionNodeResolverBinding binding) {
        String canonical = WorkflowCanonicalHashSupport.canonical(
                binding.bindingSchemaVersion(),
                binding.definitionId(),
                binding.definitionVersionId(),
                binding.nodeId(),
                binding.resolverCode().value(),
                binding.resolverVersion().value(),
                binding.resolverContractHash().value(),
                binding.strategyType(),
                binding.resolverMode(),
                binding.targetType(),
                binding.roleCode(),
                binding.organizationScopeType(),
                binding.organizationId(),
                binding.effectiveTimePolicy());
        return WorkflowCanonicalHashSupport.sha256(canonical);
    }
}
