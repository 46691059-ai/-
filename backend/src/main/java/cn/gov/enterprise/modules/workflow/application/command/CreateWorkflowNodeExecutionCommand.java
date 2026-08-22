package cn.gov.enterprise.modules.workflow.application.command;

/** Internal engine command. It is intentionally not exposed by a REST endpoint. */
public record CreateWorkflowNodeExecutionCommand(
        Long instanceId, Long nodeId, int visitNo,
        Long previousExecutionId, Long sourceTransitionId, String traceId) {}
