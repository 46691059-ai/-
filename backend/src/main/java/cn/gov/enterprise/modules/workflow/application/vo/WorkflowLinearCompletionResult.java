package cn.gov.enterprise.modules.workflow.application.vo;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;

/** Unambiguous result of completing one task in a linear workflow. */
public record WorkflowLinearCompletionResult(
        Long actionId,
        WorkflowTask.Status taskStatus,
        WorkflowNodeExecution.Status currentNodeStatus,
        WorkflowInstance.Status instanceStatus,
        Long nextNodeExecutionId,
        WorkflowNodeExecution.Status nextNodeStatus,
        Long nextTaskId,
        long eventSequence) {}
