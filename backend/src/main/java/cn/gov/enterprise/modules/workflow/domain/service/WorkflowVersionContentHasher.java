package cn.gov.enterprise.modules.workflow.domain.service;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Produces a deterministic semantic SHA-256 independent of persistence IDs and audit fields. */
public final class WorkflowVersionContentHasher {
    public String hash(WorkflowDefinition definition, WorkflowVersion version, List<WorkflowNode> nodes) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, definition.definitionCode());
        append(canonical, definition.businessType());
        append(canonical, definition.enterpriseId());
        append(canonical, version.schemaVersion());
        nodes.stream().sorted(Comparator.comparingInt(WorkflowNode::nodeOrder)
                        .thenComparing(WorkflowNode::nodeCode))
                .forEach(node -> appendNode(canonical, node));
        return digest(canonical);
    }

    public String hashGraph(
            WorkflowDefinition definition, WorkflowVersion version, List<WorkflowNode> nodes,
            List<WorkflowTransition> transitions) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, definition.definitionCode());
        append(canonical, definition.businessType());
        append(canonical, definition.enterpriseId());
        append(canonical, version.schemaVersion());
        append(canonical, version.engineMode());
        append(canonical, version.contentHashAlgorithm());
        nodes.stream().sorted(Comparator.comparingInt(WorkflowNode::nodeOrder)
                        .thenComparing(WorkflowNode::nodeCode))
                .forEach(node -> appendNode(canonical, node));
        java.util.Map<Long, String> codes = nodes.stream().collect(
                java.util.stream.Collectors.toMap(WorkflowNode::id, WorkflowNode::nodeCode));
        transitions.stream().filter(WorkflowTransition::enabled)
                .sorted(Comparator.comparing(WorkflowTransition::transitionCode))
                .forEach(item -> {
                    append(canonical, item.transitionCode());
                    append(canonical, item.transitionName());
                    append(canonical, codes.get(item.fromNodeId()));
                    append(canonical, codes.get(item.toNodeId()));
                    append(canonical, item.triggerType());
                    append(canonical, item.routeType());
                    append(canonical, item.priority());
                    append(canonical, item.conditionConfig());
                });
        return digest(canonical);
    }

    private String digest(StringBuilder canonical) {
        return WorkflowCanonicalHashSupport.sha256(canonical.toString());
    }

    private void appendNode(StringBuilder target, WorkflowNode node) {
        append(target, node.nodeCode()); append(target, node.nodeName()); append(target, node.nodeType());
        append(target, node.nodeOrder()); append(target, node.governanceNodeType());
        append(target, node.approvalMode()); append(target, node.approvalThreshold());
        append(target, node.assignmentRuleType()); append(target, node.assignmentRuleConfig());
        append(target, node.entryConditionConfig()); append(target, node.completionConditionConfig());
        append(target, node.timeoutMinutes()); append(target, node.withdrawAllowed()); append(target, node.enabled());
    }

    private void append(StringBuilder target, Object value) {
        String text = Objects.toString(value, "");
        target.append(text.length()).append(':').append(text).append('|');
    }
}
