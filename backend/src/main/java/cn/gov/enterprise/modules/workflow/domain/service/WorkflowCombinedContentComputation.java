package cn.gov.enterprise.modules.workflow.domain.service;

import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;

/** Content-hash result without persistence or publication side effects. */
public record WorkflowCombinedContentComputation(
        ResolverBindingModel resolverBindingModel,
        String graphHash,
        String manifestHash,
        String canonicalVersion,
        String canonical,
        String contentHash) {
    public WorkflowCombinedContentComputation {
        if (resolverBindingModel == null) {
            throw new IllegalArgumentException("resolverBindingModel must not be null");
        }
        graphHash = WorkflowCanonicalHashSupport.requireSha256(graphHash, "graphHash");
        contentHash = WorkflowCanonicalHashSupport.requireSha256(contentHash, "contentHash");
        if (resolverBindingModel == ResolverBindingModel.LEGACY_USER_ONLY) {
            if (manifestHash != null || canonicalVersion != null || canonical != null
                    || !graphHash.equals(contentHash)) {
                throw new IllegalArgumentException("legacy content hash must remain the graph hash");
            }
        } else {
            manifestHash = WorkflowCanonicalHashSupport.requireSha256(manifestHash, "manifestHash");
            if (!WorkflowCombinedContentHasher.CANONICAL_VERSION.equals(canonicalVersion)
                    || canonical == null || canonical.isEmpty()) {
                throw new IllegalArgumentException("combined content evidence is incomplete");
            }
        }
    }
}
