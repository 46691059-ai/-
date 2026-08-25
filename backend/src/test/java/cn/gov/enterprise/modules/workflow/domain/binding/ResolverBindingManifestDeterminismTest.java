package cn.gov.enterprise.modules.workflow.domain.binding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResolverBindingManifestDeterminismTest {
    @Test
    void inputOrderMustNotAffectCanonicalOrHash() {
        VersionNodeResolverBinding first = VersionResolverBindingCanonicalFixtures.first();
        VersionNodeResolverBinding second = VersionResolverBindingCanonicalFixtures.second();
        ResolverBindingManifestComputation ab =
                ResolverBindingManifestCanonical.compute(List.of(first, second));
        ResolverBindingManifestComputation ba =
                ResolverBindingManifestCanonical.compute(List.of(second, first));

        assertThat(ba.canonical()).isEqualTo(ab.canonical());
        assertThat(ba.manifestHash()).isEqualTo(ab.manifestHash());
    }

    @Test
    void orderAndMembershipChangesMustChangeTheManifest() {
        VersionNodeResolverBinding first = VersionResolverBindingCanonicalFixtures.first();
        VersionNodeResolverBinding second = VersionResolverBindingCanonicalFixtures.second();
        ResolverBindingManifestComputation original =
                ResolverBindingManifestCanonical.compute(List.of(first, second));
        VersionNodeResolverBinding changedOrder = VersionResolverBindingCanonicalFixtures.binding(
                second.id(), second.definitionId(), second.definitionVersionId(), second.nodeId(),
                2, "b", second.roleCode(), second.organizationId(), second.version(),
                second.bindingHash());
        ResolverBindingManifestComputation reordered =
                ResolverBindingManifestCanonical.compute(List.of(first, changedOrder));
        ResolverBindingManifestComputation removed =
                ResolverBindingManifestCanonical.compute(List.of(first));

        assertThat(reordered.manifestHash()).isNotEqualTo(original.manifestHash());
        assertThat(removed.bindingCount()).isEqualTo(1);
        assertThat(removed.manifestHash()).isNotEqualTo(original.manifestHash());
    }
}
