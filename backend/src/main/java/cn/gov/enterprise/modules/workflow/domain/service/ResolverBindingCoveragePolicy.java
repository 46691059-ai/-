package cn.gov.enterprise.modules.workflow.domain.service;

import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Publication coverage policy for enabled human-task nodes. */
public final class ResolverBindingCoveragePolicy {
    public boolean requiresResolverBinding(WorkflowNode node) {
        return node.enabled() && (node.nodeType() == WorkflowNode.NodeType.APPROVAL
                || node.nodeType() == WorkflowNode.NodeType.COUNTERSIGN);
    }

    public void validate(Long definitionId, Long definitionVersionId,
            List<WorkflowNode> nodes, List<VersionNodeResolverBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            throw new IllegalStateException("resolver binding is missing");
        }
        Map<Long, WorkflowNode> nodesById = nodes.stream().collect(
                Collectors.toMap(WorkflowNode::id, node -> node));
        Set<Long> required = nodes.stream().filter(this::requiresResolverBinding)
                .map(WorkflowNode::id).collect(Collectors.toSet());
        Set<Long> bound = new HashSet<>();
        for (VersionNodeResolverBinding binding : bindings) {
            if (!definitionId.equals(binding.definitionId())
                    || !definitionVersionId.equals(binding.definitionVersionId())) {
                throw new IllegalStateException("resolver binding belongs to another Definition or Version");
            }
            WorkflowNode node = nodesById.get(binding.nodeId());
            if (node == null || !definitionVersionId.equals(node.versionId())) {
                throw new IllegalStateException("resolver binding references an orphan node");
            }
            if (!requiresResolverBinding(node)) {
                throw new IllegalStateException("resolver binding references a non-task node");
            }
            bound.add(binding.nodeId());
        }
        Set<Long> missing = new HashSet<>(required);
        missing.removeAll(bound);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("resolver binding is missing for required nodes " + missing);
        }
        Set<Long> orphan = new HashSet<>(bound);
        orphan.removeAll(required);
        if (!orphan.isEmpty()) {
            throw new IllegalStateException("resolver binding contains orphan nodes " + orphan);
        }
    }
}
