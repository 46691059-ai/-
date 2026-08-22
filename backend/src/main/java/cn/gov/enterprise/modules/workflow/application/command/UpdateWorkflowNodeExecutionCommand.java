package cn.gov.enterprise.modules.workflow.application.command;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;

/** Internal engine command. It is intentionally not exposed by a REST endpoint. */
public record UpdateWorkflowNodeExecutionCommand(
        Long executionId, WorkflowNodeExecution.Status targetStatus,
        String result, String failureCode, int expectedVersion) {}
