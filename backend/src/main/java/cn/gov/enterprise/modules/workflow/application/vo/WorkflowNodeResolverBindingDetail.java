package cn.gov.enterprise.modules.workflow.application.vo;

import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;

public record WorkflowNodeResolverBindingDetail(
        String evidenceType, NodeResolverBinding nodeBinding,
        WorkflowResolverBinding resolverBinding, String warning) {
    public static WorkflowNodeResolverBindingDetail frozen(
            NodeResolverBinding node, WorkflowResolverBinding resolver) {
        return new WorkflowNodeResolverBindingDetail("MULTI_RESOLVER_FROZEN", node, resolver, null);
    }
    public static WorkflowNodeResolverBindingDetail legacy() {
        return new WorkflowNodeResolverBindingDetail("LEGACY_INSTANCE_BINDING", null, null,
                "Node binding was not persisted; runtime uses the exact instance binding compatibility path");
    }
}
