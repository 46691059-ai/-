package cn.gov.enterprise.modules.workflow.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** One immutable visit to a workflow node within an instance. */
public record WorkflowNodeExecution(
        Long id, String executionNo, Long instanceId, Long versionId, Long nodeId,
        String nodeCodeSnapshot, String nodeNameSnapshot, int visitNo,
        Long previousExecutionId, Long sourceTransitionId, Status status, String result,
        LocalDateTime enteredTime, LocalDateTime activatedTime, Long completedBy,
        LocalDateTime completedTime, String failureCode, String traceId, int version) {

    public enum Status { CREATED, ACTIVE, COMPLETED, REJECTED, CANCELLED, FAILED }

    public WorkflowNodeExecution {
        Objects.requireNonNull(id, "id");
        executionNo = required(executionNo, "executionNo", 100);
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(nodeId, "nodeId");
        nodeCodeSnapshot = required(nodeCodeSnapshot, "nodeCodeSnapshot", 100);
        nodeNameSnapshot = required(nodeNameSnapshot, "nodeNameSnapshot", 200);
        if (visitNo <= 0) throw new IllegalArgumentException("visitNo must be positive");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(enteredTime, "enteredTime");
        validateState(status, result, activatedTime, completedTime, failureCode);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    public static WorkflowNodeExecution created(
            Long id, String executionNo, WorkflowInstance instance, WorkflowNode node,
            int visitNo, Long previousExecutionId, Long sourceTransitionId,
            String traceId, LocalDateTime enteredTime) {
        if (!instance.versionId().equals(node.versionId())) {
            throw new IllegalArgumentException("node does not belong to instance version");
        }
        return new WorkflowNodeExecution(id, executionNo, instance.id(), instance.versionId(), node.id(),
                node.nodeCode(), node.nodeName(), visitNo, previousExecutionId, sourceTransitionId,
                Status.CREATED, null, enteredTime, null, null, null, null, traceId, 0);
    }

    public WorkflowNodeExecution activate(LocalDateTime actionTime) {
        requireStatus(Status.CREATED);
        Objects.requireNonNull(actionTime, "actionTime");
        return changed(Status.ACTIVE, null, actionTime, null, null, null);
    }

    public WorkflowNodeExecution complete(Long operatorUserId, String targetResult, LocalDateTime actionTime) {
        requireStatus(Status.ACTIVE);
        return terminal(Status.COMPLETED, operatorUserId,
                required(targetResult, "result", 40), null, actionTime);
    }

    public WorkflowNodeExecution reject(Long operatorUserId, String targetResult, LocalDateTime actionTime) {
        requireStatus(Status.ACTIVE);
        return terminal(Status.REJECTED, operatorUserId,
                required(targetResult, "result", 40), null, actionTime);
    }

    public WorkflowNodeExecution cancel(Long operatorUserId, String targetResult, LocalDateTime actionTime) {
        if (status != Status.CREATED && status != Status.ACTIVE) {
            throw new IllegalStateException("node execution in status " + status + " cannot be cancelled");
        }
        return terminal(Status.CANCELLED, operatorUserId,
                required(targetResult, "result", 40), null, actionTime);
    }

    public WorkflowNodeExecution fail(String targetFailureCode, LocalDateTime actionTime) {
        if (status != Status.CREATED && status != Status.ACTIVE) {
            throw new IllegalStateException("node execution in status " + status + " cannot fail");
        }
        String code = required(targetFailureCode, "failureCode", 64);
        return terminal(Status.FAILED, null, "FAILED", code, actionTime);
    }

    private WorkflowNodeExecution terminal(
            Status target, Long operatorUserId, String targetResult,
            String targetFailureCode, LocalDateTime actionTime) {
        Objects.requireNonNull(actionTime, "actionTime");
        if (operatorUserId != null && operatorUserId <= 0) {
            throw new IllegalArgumentException("operatorUserId must be positive");
        }
        return changed(target, targetResult, activatedTime, operatorUserId, actionTime, targetFailureCode);
    }

    private WorkflowNodeExecution changed(
            Status target, String targetResult, LocalDateTime targetActivatedTime,
            Long targetCompletedBy, LocalDateTime targetCompletedTime, String targetFailureCode) {
        return new WorkflowNodeExecution(id, executionNo, instanceId, versionId, nodeId,
                nodeCodeSnapshot, nodeNameSnapshot, visitNo, previousExecutionId, sourceTransitionId,
                target, targetResult, enteredTime, targetActivatedTime, targetCompletedBy,
                targetCompletedTime, targetFailureCode, traceId, version + 1);
    }

    private void requireStatus(Status required) {
        if (status != required) {
            throw new IllegalStateException("node execution in status " + status + " requires " + required);
        }
    }

    private static void validateState(
            Status status, String result, LocalDateTime activatedTime,
            LocalDateTime completedTime, String failureCode) {
        boolean terminal = status == Status.COMPLETED || status == Status.REJECTED
                || status == Status.CANCELLED || status == Status.FAILED;
        if (status == Status.CREATED && (activatedTime != null || completedTime != null)) {
            throw new IllegalArgumentException("CREATED execution cannot have activity timestamps");
        }
        if (status == Status.ACTIVE && (activatedTime == null || completedTime != null)) {
            throw new IllegalArgumentException("ACTIVE execution requires activatedTime only");
        }
        if (terminal && (completedTime == null || result == null || result.isBlank())) {
            throw new IllegalArgumentException("terminal execution requires result and completedTime");
        }
        if (!terminal && result != null) throw new IllegalArgumentException("open execution cannot have result");
        if (failureCode != null && status != Status.FAILED) {
            throw new IllegalArgumentException("failureCode is only valid for FAILED execution");
        }
        if (status == Status.FAILED && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException("FAILED execution requires failureCode");
        }
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
