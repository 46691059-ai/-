package cn.gov.enterprise.modules.workflow.domain.service;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Publication/start validation for deterministic APPROVAL-only linear graphs. */
public final class WorkflowLinearGraphValidator {
    public WorkflowNode validateAndFindEntry(
            Long versionId, List<WorkflowNode> nodes, List<WorkflowTransition> transitions) {
        List<WorkflowNode> enabledNodes = nodes.stream().filter(WorkflowNode::enabled).toList();
        if (enabledNodes.isEmpty()) throw new IllegalStateException("linear workflow has no enabled node");
        Map<Long, WorkflowNode> byId = new HashMap<>();
        for (WorkflowNode node : enabledNodes) {
            if (!versionId.equals(node.versionId()) || node.nodeType() != WorkflowNode.NodeType.APPROVAL
                    || node.approvalMode() != WorkflowNode.ApprovalMode.SINGLE) {
                throw new IllegalStateException("linear workflow supports enabled SINGLE APPROVAL nodes only");
            }
            byId.put(node.id(), node);
        }
        Map<Long, Integer> incoming = new HashMap<>();
        Map<Long, WorkflowTransition> outgoing = new HashMap<>();
        enabledNodes.forEach(node -> incoming.put(node.id(), 0));
        for (WorkflowTransition edge : transitions.stream().filter(WorkflowTransition::enabled).toList()) {
            if (!versionId.equals(edge.versionId()) || !byId.containsKey(edge.fromNodeId())
                    || !byId.containsKey(edge.toNodeId())
                    || edge.triggerType() != WorkflowTransition.TriggerType.APPROVE
                    || edge.routeType() != WorkflowTransition.RouteType.DIRECT
                    || edge.conditionConfig() != null) {
                throw new IllegalStateException("linear workflow contains an invalid transition");
            }
            if (outgoing.put(edge.fromNodeId(), edge) != null) {
                throw new IllegalStateException("linear workflow node has multiple APPROVE transitions");
            }
            incoming.compute(edge.toNodeId(), (key, value) -> value == null ? 1 : value + 1);
        }
        List<WorkflowNode> entries = enabledNodes.stream()
                .filter(node -> incoming.getOrDefault(node.id(), 0) == 0).toList();
        if (entries.size() != 1) throw new IllegalStateException("linear workflow requires one entry node");
        WorkflowNode cursor = entries.getFirst();
        Set<Long> visited = new HashSet<>();
        while (cursor != null) {
            if (!visited.add(cursor.id())) throw new IllegalStateException("linear workflow cannot contain a cycle");
            WorkflowTransition edge = outgoing.get(cursor.id());
            cursor = edge == null ? null : byId.get(edge.toNodeId());
        }
        if (visited.size() != enabledNodes.size()) {
            throw new IllegalStateException("linear workflow contains unreachable nodes");
        }
        return entries.getFirst();
    }
}
