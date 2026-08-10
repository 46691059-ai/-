package cn.gov.enterprise.modules.investment.domain.model;

public record WorkflowBinding(
        Long id, Long decisionId, Long snapshotId, int attemptNo,
        String workflowInstanceId, String idempotencyKey, Status status,
        long lastEventSequence, int version) {
    public enum Status { STARTING, RUNNING, COMPLETED, RETURNED, WITHDRAWN, CANCELLED, TERMINATED, START_FAILED }
}
