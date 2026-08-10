package cn.gov.enterprise.modules.investment.application.workflow;

import java.time.LocalDateTime;

public record WorkflowEvent(
        String eventId, long sequence, String eventType, String workflowInstanceId,
        Long decisionId, Long snapshotId, int attemptNo, String result,
        String operatorId, LocalDateTime occurredTime, String traceId) {}
