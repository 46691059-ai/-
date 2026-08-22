package cn.gov.enterprise.modules.workflow.domain.service;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import java.time.LocalDateTime;
import java.util.Objects;

/** Pure domain operations for one node visit. */
public final class NodeExecutionService {
    public WorkflowNodeExecution enter(
            Long id, String executionNo, WorkflowInstance instance, WorkflowNode node,
            int visitNo, Long previousExecutionId, Long sourceTransitionId,
            String traceId, LocalDateTime enteredTime) {
        Objects.requireNonNull(enteredTime, "enteredTime");
        return WorkflowNodeExecution.created(id, executionNo, instance, node, visitNo,
                previousExecutionId, sourceTransitionId, traceId, enteredTime)
                .activate(enteredTime);
    }

    public WorkflowNodeExecution complete(
            WorkflowNodeExecution execution, Long operatorUserId, LocalDateTime actionTime) {
        return Objects.requireNonNull(execution, "execution")
                .complete(operatorUserId, "APPROVED", actionTime);
    }
}
