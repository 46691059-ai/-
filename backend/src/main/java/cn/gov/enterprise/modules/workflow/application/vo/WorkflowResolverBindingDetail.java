package cn.gov.enterprise.modules.workflow.application.vo;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;

/** Non-sensitive view of the resolver identity frozen on a Workflow Instance. */
public record WorkflowResolverBindingDetail(
        Long instanceId, String resolverCode, String resolverVersion, String contractHash) {

    public static WorkflowResolverBindingDetail from(ResolverVersionBinding binding) {
        return new WorkflowResolverBindingDetail(binding.instanceId(),
                binding.resolverCode().value(), binding.resolverVersion().value(),
                binding.contractHash().value());
    }
}
