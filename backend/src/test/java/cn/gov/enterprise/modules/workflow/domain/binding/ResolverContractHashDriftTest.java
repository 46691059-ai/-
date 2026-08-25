package cn.gov.enterprise.modules.workflow.domain.binding;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCombinedContentHasher;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResolverContractHashDriftTest {
    @Test
    void resolverContractDriftMustPropagateThroughAllHashLevels() {
        VersionNodeResolverBinding original = VersionResolverBindingCanonicalFixtures.first();
        VersionNodeResolverBinding drifted = VersionResolverBindingCanonicalFixtures.binding(
                original.id(), original.definitionId(), original.definitionVersionId(),
                original.nodeId(), original.bindingOrder(), "c", original.roleCode(),
                original.organizationId(), original.version(), original.bindingHash());
        ResolverBindingManifestComputation originalManifest =
                ResolverBindingManifestCanonical.compute(List.of(original));
        ResolverBindingManifestComputation driftedManifest =
                ResolverBindingManifestCanonical.compute(List.of(drifted));
        WorkflowCombinedContentHasher combined = new WorkflowCombinedContentHasher();
        String graphHash = "9".repeat(64);

        assertThat(VersionNodeResolverBindingCanonical.compute(drifted).bindingHash())
                .isNotEqualTo(VersionNodeResolverBindingCanonical.compute(original).bindingHash());
        assertThat(driftedManifest.manifestHash()).isNotEqualTo(originalManifest.manifestHash());
        assertThat(combined.compute(ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                graphHash, driftedManifest).contentHash()).isNotEqualTo(combined.compute(
                        ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                        graphHash, originalManifest).contentHash());
    }
}
