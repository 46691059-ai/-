package cn.gov.enterprise.modules.workflow.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifestCanonical;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifestComputation;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionResolverBindingCanonicalFixtures;
import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowCombinedContentHasherTest {
    private static final String GRAPH_HASH =
            "67c5bf892d3e5d57d3cb94f3c475096aee65aa41aaafda1dfa0dfc168e776567";
    private static final String COMBINED_CANONICAL =
            "28:WORKFLOW_COMBINED_CONTENT_V1|32:VERSION_RESOLVER_BINDING_CAPABLE|64:"
                    + GRAPH_HASH + "|64:"
                    + VersionResolverBindingCanonicalFixtures.MANIFEST_HASH + "|";
    private static final String COMBINED_HASH =
            "d8b7f7b58e38a7776fbc8af3dec5e7c620227d59027ccb00d742989a5b96242b";

    @Test
    void goldenCombinedCanonicalAndHashMustRemainFrozen() {
        WorkflowCombinedContentComputation result = new WorkflowCombinedContentHasher().compute(
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE, GRAPH_HASH, manifest());

        assertThat(result.canonicalVersion())
                .isEqualTo(WorkflowCombinedContentHasher.CANONICAL_VERSION);
        assertThat(result.canonical()).isEqualTo(COMBINED_CANONICAL);
        assertThat(result.contentHash()).isEqualTo(COMBINED_HASH);
    }

    @Test
    void graphManifestAndModelDriftMustNotCollide() {
        WorkflowCombinedContentHasher hasher = new WorkflowCombinedContentHasher();
        String original = hasher.compute(ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                GRAPH_HASH, manifest()).contentHash();
        String graphDrift = hasher.compute(ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                "8".repeat(64), manifest()).contentHash();
        VersionNodeResolverBinding changed = VersionResolverBindingCanonicalFixtures.binding(
                3L, 10L, 20L, 30L, 1, "c", "ROLE_APPROVER", 99L, 0,
                "0".repeat(64));
        String manifestDrift = hasher.compute(
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE, GRAPH_HASH,
                ResolverBindingManifestCanonical.compute(List.of(changed))).contentHash();

        assertThat(graphDrift).isNotEqualTo(original);
        assertThat(manifestDrift).isNotEqualTo(original);
        assertThat(hasher.compute(ResolverBindingModel.LEGACY_USER_ONLY,
                GRAPH_HASH, null).contentHash()).isEqualTo(GRAPH_HASH).isNotEqualTo(original);
    }

    @Test
    void emptyOrCrossModelManifestMustFailClosed() {
        WorkflowCombinedContentHasher hasher = new WorkflowCombinedContentHasher();
        assertThatThrownBy(() -> hasher.compute(
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE, GRAPH_HASH, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> hasher.compute(
                ResolverBindingModel.LEGACY_USER_ONLY, GRAPH_HASH, manifest()))
                .hasMessageContaining("must not have");
        assertThatThrownBy(() -> hasher.compute(
                ResolverBindingModel.LEGACY_USER_ONLY, "BAD", null))
                .hasMessageContaining("lowercase SHA-256");
    }

    private ResolverBindingManifestComputation manifest() {
        return ResolverBindingManifestCanonical.compute(List.of(
                VersionResolverBindingCanonicalFixtures.first(),
                VersionResolverBindingCanonicalFixtures.second()));
    }
}
