package cn.gov.enterprise.modules.workflow.domain.service;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves the sole deterministic DIRECT edge for the linear engine. */
public final class TransitionResolver {
    public Optional<WorkflowTransition> resolveApproved(
            WorkflowNodeExecution execution, List<WorkflowTransition> outgoing) {
        Objects.requireNonNull(execution, "execution");
        if (execution.status() != WorkflowNodeExecution.Status.COMPLETED) {
            throw new IllegalStateException("transition can only be resolved for a completed execution");
        }
        List<WorkflowTransition> candidates = Objects.requireNonNull(outgoing, "outgoing").stream()
                .filter(WorkflowTransition::enabled)
                .filter(item -> item.triggerType() == WorkflowTransition.TriggerType.APPROVE)
                .toList();
        if (candidates.size() > 1) {
            throw new IllegalStateException("linear workflow node has multiple APPROVE transitions");
        }
        if (candidates.isEmpty()) return Optional.empty();
        WorkflowTransition selected = candidates.getFirst();
        if (!execution.versionId().equals(selected.versionId())
                || !execution.nodeId().equals(selected.fromNodeId())
                || selected.routeType() != WorkflowTransition.RouteType.DIRECT
                || selected.conditionConfig() != null) {
            throw new IllegalStateException("transition is invalid for the current linear execution");
        }
        return Optional.of(selected);
    }
}
