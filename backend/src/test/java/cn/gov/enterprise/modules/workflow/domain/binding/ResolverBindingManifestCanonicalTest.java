package cn.gov.enterprise.modules.workflow.domain.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResolverBindingManifestCanonicalTest {
    @Test
    void goldenManifestCanonicalAndHashMustRemainFrozen() {
        ResolverBindingManifestComputation result = ResolverBindingManifestCanonical.compute(
                List.of(VersionResolverBindingCanonicalFixtures.first(),
                        VersionResolverBindingCanonicalFixtures.second()));

        assertThat(result.bindingCount()).isEqualTo(2);
        assertThat(result.canonicalVersion()).isEqualTo(ResolverBindingManifest.CANONICAL_VERSION);
        assertThat(result.canonical())
                .isEqualTo(VersionResolverBindingCanonicalFixtures.MANIFEST_CANONICAL);
        assertThat(result.manifestHash())
                .isEqualTo(VersionResolverBindingCanonicalFixtures.MANIFEST_HASH);
        assertThat(result.orderedBindings()).extracting(VersionNodeResolverBindingComputation::bindingHash)
                .containsExactly(VersionResolverBindingCanonicalFixtures.BINDING_ONE_HASH,
                        VersionResolverBindingCanonicalFixtures.BINDING_TWO_HASH);
    }

    @Test
    void emptyMixedOwnershipAndDuplicateBindingsMustFailFast() {
        VersionNodeResolverBinding first = VersionResolverBindingCanonicalFixtures.first();
        VersionNodeResolverBinding second = VersionResolverBindingCanonicalFixtures.second();
        assertThatThrownBy(() -> ResolverBindingManifestCanonical.compute(List.of()))
                .hasMessageContaining("must not be empty");
        assertThatThrownBy(() -> ResolverBindingManifestCanonical.compute(List.of(first,
                VersionResolverBindingCanonicalFixtures.binding(
                        3L, 10L, 21L, 40L, 1, "b", "ROLE_REVIEWER", 100L, 0,
                        "1".repeat(64)))))
                .hasMessageContaining("one Version");
        assertThatThrownBy(() -> ResolverBindingManifestCanonical.compute(List.of(first,
                VersionResolverBindingCanonicalFixtures.binding(
                        4L, 10L, 20L, 30L, 1, "b", "ROLE_REVIEWER", 100L, 0,
                        "2".repeat(64)))))
                .hasMessageContaining("node and bindingOrder");
        assertThatThrownBy(() -> ResolverBindingManifestCanonical.compute(List.of(first,
                VersionResolverBindingCanonicalFixtures.binding(
                        5L, 10L, 20L, 30L, 2, "a", "ROLE_APPROVER", 99L, 0,
                        "3".repeat(64)))))
                .hasMessageContaining("logical resolver binding");
        assertThat(second).isNotNull();
    }
}
