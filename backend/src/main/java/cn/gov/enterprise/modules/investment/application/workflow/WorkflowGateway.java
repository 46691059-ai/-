package cn.gov.enterprise.modules.investment.application.workflow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Investment-facing port; Workflow remains an external bounded context. */
public interface WorkflowGateway {
    WorkflowInstance start(StartCommand command);
    WorkflowInstance query(String workflowInstanceId);
    List<WorkflowTask> queryTasks(String workflowInstanceId);
    void withdraw(String workflowInstanceId, String reason, String idempotencyKey);

    record StartCommand(String businessType, String businessId, String businessKey,
                        Long enterpriseId, Long snapshotId, String snapshotHash, int attemptNo,
                        String definitionKey, int definitionVersion, Long initiatorId,
                        Map<String, Object> variables, String idempotencyKey, String traceId) {}
    record WorkflowInstance(String instanceId, String status, LocalDateTime startedTime) {}
    record WorkflowTask(String taskId, String nodeCode, String taskName, String assigneeId, String status) {}
}
