package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import cn.gov.enterprise.modules.investment.application.workflow.WorkflowEvent;
import cn.gov.enterprise.modules.investment.application.workflow.WorkflowGateway;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkflowAdapter implements WorkflowGateway {
    private final WorkflowClient client;
    private final WorkflowInboxService inboxService;
    public WorkflowAdapter(WorkflowClient client, WorkflowInboxService inboxService) {
        this.client = client; this.inboxService = inboxService;
    }
    @Override public WorkflowInstance start(StartCommand command) { return client.start(command); }
    @Override public WorkflowInstance query(String id) { return client.query(id); }
    @Override public List<WorkflowTask> queryTasks(String id) { return client.tasks(id); }
    @Override public void withdraw(String id, String reason, String key) { client.withdraw(id, reason, key); }
    public WorkflowInboxService.Result receiveApprovalResult(WorkflowEvent event) { return inboxService.receive(event); }
    public WorkflowInboxService.Result receiveApprovalResult(WorkflowEvent event,String payloadJson,String payloadHash) {
        return inboxService.receive(event,payloadJson,payloadHash);
    }
}
