package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import cn.gov.enterprise.modules.investment.application.workflow.WorkflowGateway;
import cn.gov.enterprise.modules.investment.application.workflow.WorkflowEvent;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpWorkflowClient implements WorkflowClient {
    private final RestClient client;
    public HttpWorkflowClient(RestClient workflowRestClient) { this.client = workflowRestClient; }

    @Override public WorkflowGateway.WorkflowInstance start(WorkflowGateway.StartCommand command) {
        return client.post().uri("/api/workflow/v1/process-instances").header("Idempotency-Key", command.idempotencyKey())
                .body(command).retrieve().body(WorkflowGateway.WorkflowInstance.class);
    }
    @Override public WorkflowGateway.WorkflowInstance query(String id) {
        return client.get().uri("/api/workflow/v1/process-instances/{id}", id).retrieve()
                .body(WorkflowGateway.WorkflowInstance.class);
    }
    @Override public List<WorkflowGateway.WorkflowTask> tasks(String id) {
        var result = client.get().uri("/api/workflow/v1/process-instances/{id}/tasks", id).retrieve()
                .body(WorkflowGateway.WorkflowTask[].class);
        return result == null ? List.of() : Arrays.asList(result);
    }
    @Override public List<WorkflowEvent> events(String id, long fromSequence, long toSequence) {
        var result = client.get().uri(builder -> builder
                        .path("/api/workflow/v1/process-instances/{id}/events")
                        .queryParam("fromSequence", fromSequence)
                        .queryParam("toSequence", toSequence).build(id))
                .retrieve().body(WorkflowEvent[].class);
        return result == null ? List.of() : Arrays.asList(result);
    }
    @Override public void withdraw(String id, String reason, String key) {
        client.post().uri("/api/workflow/v1/process-instances/{id}/withdrawals", id).header("Idempotency-Key", key)
                .body(new WithdrawRequest(reason)).retrieve().toBodilessEntity();
    }
    private record WithdrawRequest(String reason) {}
}
