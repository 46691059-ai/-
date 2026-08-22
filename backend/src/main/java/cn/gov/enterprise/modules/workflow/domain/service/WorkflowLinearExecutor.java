package cn.gov.enterprise.modules.workflow.domain.service;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Pure decision component. Persistence and transaction handling remain in the application layer. */
public final class WorkflowLinearExecutor {
    private final NodeExecutionService nodeExecutionService = new NodeExecutionService();
    private final TransitionResolver transitionResolver = new TransitionResolver();

    public Completion complete(
            WorkflowTask task, WorkflowNodeExecution execution, Long operatorUserId,
            LocalDateTime actionTime, List<WorkflowTransition> outgoing) {
        if (task.nodeExecutionId() == null || !task.nodeExecutionId().equals(execution.id())
                || !task.instanceId().equals(execution.instanceId())
                || !task.versionId().equals(execution.versionId())
                || !task.nodeId().equals(execution.nodeId())) {
            throw new IllegalStateException("task does not belong to the current node execution");
        }
        WorkflowTask completedTask = task.approve(operatorUserId, actionTime);
        WorkflowNodeExecution completedExecution = nodeExecutionService.complete(
                execution, operatorUserId, actionTime);
        Optional<WorkflowTransition> transition = transitionResolver.resolveApproved(
                completedExecution, outgoing);
        return new Completion(completedTask, completedExecution, transition);
    }

    public record Completion(
            WorkflowTask task, WorkflowNodeExecution execution,
            Optional<WorkflowTransition> transition) {}
}
