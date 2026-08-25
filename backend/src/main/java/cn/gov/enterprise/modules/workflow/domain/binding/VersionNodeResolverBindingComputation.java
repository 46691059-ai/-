package cn.gov.enterprise.modules.workflow.domain.binding;

import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCanonicalHashSupport;
import java.util.Objects;

/** Canonical evidence calculated for one Version-owned node resolver binding. */
public record VersionNodeResolverBindingComputation(
        VersionNodeResolverBinding binding,
        String canonicalVersion,
        String canonical,
        String bindingHash) {
    public VersionNodeResolverBindingComputation {
        Objects.requireNonNull(binding, "binding");
        if (!VersionNodeResolverBinding.SCHEMA_VERSION.equals(canonicalVersion)) {
            throw new IllegalArgumentException("unsupported binding canonicalVersion");
        }
        if (canonical == null || canonical.isEmpty()) {
            throw new IllegalArgumentException("binding canonical must not be empty");
        }
        bindingHash = WorkflowCanonicalHashSupport.requireSha256(bindingHash, "bindingHash");
    }
}
