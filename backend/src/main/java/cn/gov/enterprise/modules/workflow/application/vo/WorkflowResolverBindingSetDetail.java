package cn.gov.enterprise.modules.workflow.application.vo;

import cn.gov.enterprise.modules.workflow.domain.assignment.*;
import java.util.List;

public record WorkflowResolverBindingSetDetail(
        String evidenceType, WorkflowResolverBindingSet bindingSet,
        List<WorkflowResolverBinding> resolverBindings,
        List<NodeResolverBinding> nodeBindings, List<String> warnings) {

    public static WorkflowResolverBindingSetDetail frozen(
            WorkflowResolverBindingSet set, List<WorkflowResolverBinding> resolvers,
            List<NodeResolverBinding> nodes) {
        return new WorkflowResolverBindingSetDetail("MULTI_RESOLVER_FROZEN", set,
                List.copyOf(resolvers), List.copyOf(nodes), List.of());
    }

    public static WorkflowResolverBindingSetDetail legacy(boolean instanceBinding) {
        return new WorkflowResolverBindingSetDetail(
                instanceBinding ? "LEGACY_INSTANCE_BINDING" : "LEGACY_UNPROVEN",
                null, List.of(), List.of(), List.of("No node-level resolver evidence was persisted"));
    }
}
