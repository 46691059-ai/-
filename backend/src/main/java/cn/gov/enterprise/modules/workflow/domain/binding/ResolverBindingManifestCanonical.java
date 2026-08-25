package cn.gov.enterprise.modules.workflow.domain.binding;

import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCanonicalHashSupport;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** VERSION_RESOLVER_BINDING_MANIFEST_V1 ordering and canonical contract. */
public final class ResolverBindingManifestCanonical {
    public static final Comparator<VersionNodeResolverBinding> SORT_ORDER =
            Comparator.comparing(VersionNodeResolverBinding::nodeId)
                    .thenComparingInt(VersionNodeResolverBinding::bindingOrder)
                    .thenComparing(VersionNodeResolverBinding::bindingHash);

    private ResolverBindingManifestCanonical() { }

    public static ResolverBindingManifestComputation compute(
            List<VersionNodeResolverBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            throw new IllegalArgumentException("version resolver bindings must not be empty");
        }
        List<VersionNodeResolverBinding> ordered = bindings.stream()
                .map(item -> Objects.requireNonNull(item, "binding"))
                .sorted(SORT_ORDER)
                .toList();
        Long definitionId = ordered.getFirst().definitionId();
        Long definitionVersionId = ordered.getFirst().definitionVersionId();
        Set<String> slots = new HashSet<>();
        Set<String> logicalBindings = new HashSet<>();
        for (VersionNodeResolverBinding binding : ordered) {
            if (!definitionId.equals(binding.definitionId())
                    || !definitionVersionId.equals(binding.definitionVersionId())) {
                throw new IllegalArgumentException("manifest bindings must belong to one Version");
            }
            if (!slots.add(binding.nodeId() + ":" + binding.bindingOrder())) {
                throw new IllegalArgumentException("duplicate node and bindingOrder");
            }
            if (!logicalBindings.add(VersionNodeResolverBindingCanonical.logicalFingerprint(binding))) {
                throw new IllegalArgumentException("duplicate logical resolver binding");
            }
        }
        List<VersionNodeResolverBindingComputation> computed = ordered.stream()
                .map(VersionNodeResolverBindingCanonical::compute)
                .toList();
        StringBuilder canonical = new StringBuilder();
        WorkflowCanonicalHashSupport.append(canonical, ResolverBindingManifest.CANONICAL_VERSION);
        WorkflowCanonicalHashSupport.append(canonical, computed.size());
        computed.forEach(item -> WorkflowCanonicalHashSupport.append(canonical, item.bindingHash()));
        String text = canonical.toString();
        return new ResolverBindingManifestComputation(
                definitionId, definitionVersionId, computed, computed.size(),
                ResolverBindingManifest.CANONICAL_VERSION, text,
                WorkflowCanonicalHashSupport.sha256(text));
    }
}
