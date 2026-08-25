package cn.gov.enterprise.modules.workflow.domain.service;

import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifestComputation;
import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import java.util.Objects;

/** Selects legacy or RC2 Graph-plus-Manifest content-hash semantics. */
public final class WorkflowCombinedContentHasher {
    public static final String CANONICAL_VERSION = "WORKFLOW_COMBINED_CONTENT_V1";

    public WorkflowCombinedContentComputation compute(
            ResolverBindingModel model,
            String graphHash,
            ResolverBindingManifestComputation manifest) {
        Objects.requireNonNull(model, "model");
        WorkflowCanonicalHashSupport.requireSha256(graphHash, "graphHash");
        if (model == ResolverBindingModel.LEGACY_USER_ONLY) {
            if (manifest != null) {
                throw new IllegalArgumentException("legacy content must not have a resolver manifest");
            }
            return new WorkflowCombinedContentComputation(
                    model, graphHash, null, null, null, graphHash);
        }
        Objects.requireNonNull(manifest, "manifest");
        String canonical = WorkflowCanonicalHashSupport.canonical(
                CANONICAL_VERSION, model, graphHash, manifest.manifestHash());
        return new WorkflowCombinedContentComputation(
                model, graphHash, manifest.manifestHash(), CANONICAL_VERSION,
                canonical, WorkflowCanonicalHashSupport.sha256(canonical));
    }
}
