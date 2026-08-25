package cn.gov.enterprise.modules.workflow.domain.binding;

import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCanonicalHashSupport;
import java.util.List;

/** Complete deterministic resolver-binding evidence prepared for a future release transaction. */
public record ResolverBindingManifestComputation(
        Long definitionId,
        Long definitionVersionId,
        List<VersionNodeResolverBindingComputation> orderedBindings,
        int bindingCount,
        String canonicalVersion,
        String canonical,
        String manifestHash) {
    public ResolverBindingManifestComputation {
        if (definitionId == null || definitionId <= 0) {
            throw new IllegalArgumentException("definitionId must be positive");
        }
        if (definitionVersionId == null || definitionVersionId <= 0) {
            throw new IllegalArgumentException("definitionVersionId must be positive");
        }
        orderedBindings = List.copyOf(orderedBindings);
        if (orderedBindings.isEmpty() || bindingCount != orderedBindings.size()) {
            throw new IllegalArgumentException("bindingCount must match non-empty bindings");
        }
        if (!ResolverBindingManifest.CANONICAL_VERSION.equals(canonicalVersion)) {
            throw new IllegalArgumentException("unsupported manifest canonicalVersion");
        }
        if (canonical == null || canonical.isEmpty()) {
            throw new IllegalArgumentException("manifest canonical must not be empty");
        }
        manifestHash = WorkflowCanonicalHashSupport.requireSha256(manifestHash, "manifestHash");
    }
}
