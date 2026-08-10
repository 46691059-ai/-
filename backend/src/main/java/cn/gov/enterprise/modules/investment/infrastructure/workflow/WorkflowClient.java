package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import cn.gov.enterprise.modules.investment.application.workflow.WorkflowGateway;
import cn.gov.enterprise.modules.investment.application.workflow.WorkflowEvent;
import java.util.List;

/** Low-level HTTP client contract, mockable in tests. */
public interface WorkflowClient {
    WorkflowGateway.WorkflowInstance start(WorkflowGateway.StartCommand command);
    WorkflowGateway.WorkflowInstance query(String instanceId);
    List<WorkflowGateway.WorkflowTask> tasks(String instanceId);
    List<WorkflowEvent> events(String instanceId, long fromSequence, long toSequence);
    void withdraw(String instanceId, String reason, String idempotencyKey);
}
