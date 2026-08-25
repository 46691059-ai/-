package cn.gov.enterprise.modules.workflow.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** Materialized task for a Workflow Lite approval node. */
public record WorkflowTask(
        Long id, String taskNo, Long instanceId, Long versionId, Long nodeId, Long nodeExecutionId,
        String nodeCodeSnapshot, String nodeNameSnapshot, int taskRound,
        String participantKey, Long assigneeUserId, String candidateSnapshot,
        Status status, String allowedActions, LocalDateTime claimedTime,
        LocalDateTime dueTime, Long completedBy, LocalDateTime completedTime,
        String decisionResult, int version, AssignmentMode assignmentMode) {

    public enum Status { PENDING, CLAIMED, APPROVED, REJECTED, CANCELLED, EXPIRED }
    public enum AssignmentMode { DIRECT, CANDIDATE_POOL }

    public WorkflowTask {
        Objects.requireNonNull(id, "id");
        taskNo = required(taskNo, "taskNo", 100);
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(nodeId, "nodeId");
        nodeCodeSnapshot = required(nodeCodeSnapshot, "nodeCodeSnapshot", 100);
        nodeNameSnapshot = required(nodeNameSnapshot, "nodeNameSnapshot", 200);
        if (taskRound <= 0) throw new IllegalArgumentException("taskRound must be positive");
        participantKey = required(participantKey, "participantKey", 128);
        candidateSnapshot = required(candidateSnapshot, "candidateSnapshot", 65535);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(assignmentMode, "assignmentMode");
        allowedActions = required(allowedActions, "allowedActions", 200);
        if (status == Status.CLAIMED && (assigneeUserId == null || claimedTime == null)) {
            throw new IllegalArgumentException("claimed task requires assignee and claimedTime");
        }
        if (assignmentMode == AssignmentMode.DIRECT
                && nodeExecutionId != null && assigneeUserId == null) {
            throw new IllegalArgumentException("linear DIRECT task requires an assignee");
        }
        if (assignmentMode == AssignmentMode.CANDIDATE_POOL) {
            if (nodeExecutionId == null) {
                throw new IllegalArgumentException("Candidate Pool task requires node execution");
            }
            boolean unclaimed = status == Status.PENDING
                    && assigneeUserId == null && claimedTime == null;
            boolean claimed = status != Status.PENDING
                    && assigneeUserId != null && claimedTime != null;
            if (!unclaimed && !claimed) {
                throw new IllegalArgumentException(
                        "Candidate Pool task claim state is inconsistent");
            }
        }
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    /** Compatibility constructor for all V2.5-V2.6.5 DIRECT task callers. */
    public WorkflowTask(
            Long id, String taskNo, Long instanceId, Long versionId, Long nodeId,
            Long nodeExecutionId, String nodeCodeSnapshot, String nodeNameSnapshot,
            int taskRound, String participantKey, Long assigneeUserId, String candidateSnapshot,
            Status status, String allowedActions, LocalDateTime claimedTime,
            LocalDateTime dueTime, Long completedBy, LocalDateTime completedTime,
            String decisionResult, int version) {
        this(id, taskNo, instanceId, versionId, nodeId, nodeExecutionId,
                nodeCodeSnapshot, nodeNameSnapshot, taskRound, participantKey,
                assigneeUserId, candidateSnapshot, status, allowedActions, claimedTime,
                dueTime, completedBy, completedTime, decisionResult, version,
                AssignmentMode.DIRECT);
    }

    /** Compatibility constructor for V2.5 tasks without an execution reference. */
    public WorkflowTask(
            Long id, String taskNo, Long instanceId, Long versionId, Long nodeId,
            String nodeCodeSnapshot, String nodeNameSnapshot, int taskRound,
            String participantKey, Long assigneeUserId, String candidateSnapshot,
            Status status, String allowedActions, LocalDateTime claimedTime,
            LocalDateTime dueTime, Long completedBy, LocalDateTime completedTime,
            String decisionResult, int version) {
        this(id, taskNo, instanceId, versionId, nodeId, null, nodeCodeSnapshot,
                nodeNameSnapshot, taskRound, participantKey, assigneeUserId,
                candidateSnapshot, status, allowedActions, claimedTime, dueTime,
                completedBy, completedTime, decisionResult, version, AssignmentMode.DIRECT);
    }

    public static WorkflowTask pending(
            Long id, String taskNo, WorkflowInstance instance, WorkflowNode node,
            String participantKey, String candidateSnapshot, LocalDateTime dueTime) {
        return new WorkflowTask(id, taskNo, instance.id(), instance.versionId(), node.id(), null,
                node.nodeCode(), node.nodeName(), 1, participantKey, null, candidateSnapshot,
                Status.PENDING, "CLAIM,APPROVE,REJECT", null, dueTime,
                null, null, null, 0, AssignmentMode.DIRECT);
    }

    public static WorkflowTask pendingLinear(
            Long id, String taskNo, WorkflowInstance instance, WorkflowNode node,
            WorkflowNodeExecution execution, Long assigneeUserId, String candidateSnapshot,
            LocalDateTime dueTime) {
        if (instance.engineMode() != WorkflowEngineMode.MULTI_NODE_LINEAR_V1
                || !instance.id().equals(execution.instanceId())
                || !node.id().equals(execution.nodeId())
                || assigneeUserId == null || assigneeUserId <= 0) {
            throw new IllegalArgumentException("linear task requires matching execution and explicit assignee");
        }
        String participantKey = node.nodeCode() + ":" + execution.visitNo() + ":1";
        return new WorkflowTask(id, taskNo, instance.id(), instance.versionId(), node.id(),
                execution.id(), node.nodeCode(), node.nodeName(), 1, participantKey,
                assigneeUserId, required(candidateSnapshot, "candidateSnapshot", 65535),
                Status.PENDING, "APPROVE,REJECT", null, dueTime,
                null, null, null, 0, AssignmentMode.DIRECT);
    }

    /** Materializes an unclaimed ROLE Candidate Pool task after a Node is ACTIVE. */
    public static WorkflowTask pendingCandidatePool(
            Long id, String taskNo, WorkflowInstance instance,
            WorkflowNodeExecution execution, String candidateSnapshot,
            LocalDateTime dueTime) {
        if (instance.engineMode() != WorkflowEngineMode.MULTI_NODE_LINEAR_V1
                || !instance.id().equals(execution.instanceId())
                || !instance.versionId().equals(execution.versionId())
                || execution.status() != WorkflowNodeExecution.Status.ACTIVE
                || execution.activatedTime() == null) {
            throw new IllegalArgumentException(
                    "Candidate Pool task requires a matching ACTIVE execution");
        }
        String participantKey = execution.nodeCodeSnapshot() + ":"
                + execution.visitNo() + ":ROLE_POOL";
        return new WorkflowTask(id, taskNo, instance.id(), instance.versionId(),
                execution.nodeId(), execution.id(), execution.nodeCodeSnapshot(),
                execution.nodeNameSnapshot(), 1, participantKey, null,
                required(candidateSnapshot, "candidateSnapshot", 65535), Status.PENDING,
                "CLAIM,APPROVE,REJECT", null, dueTime, null, null, null, 0,
                AssignmentMode.CANDIDATE_POOL);
    }

    public WorkflowTask approve(Long operatorUserId, LocalDateTime actionTime) {
        requireProcessable("APPROVE");
        return complete(Status.APPROVED, "APPROVED", operatorUserId, actionTime);
    }

    /** Claims a Candidate Pool task without approving it. */
    public WorkflowTask claim(Long claimantUserId, LocalDateTime claimTime) {
        if (assignmentMode != AssignmentMode.CANDIDATE_POOL) {
            throw new IllegalStateException("DIRECT_ASSIGNMENT_NOT_CLAIMABLE");
        }
        if (status != Status.PENDING || assigneeUserId != null || claimedTime != null) {
            throw new IllegalStateException("task is not claimable");
        }
        Objects.requireNonNull(claimantUserId, "claimantUserId");
        Objects.requireNonNull(claimTime, "claimTime");
        if (claimantUserId <= 0) throw new IllegalArgumentException("claimantUserId must be positive");
        return new WorkflowTask(id, taskNo, instanceId, versionId, nodeId, nodeExecutionId,
                nodeCodeSnapshot, nodeNameSnapshot, taskRound, participantKey,
                claimantUserId, candidateSnapshot, Status.CLAIMED, allowedActions,
                claimTime, dueTime, null, null, null, version, assignmentMode);
    }

    public WorkflowTask reject(Long operatorUserId, LocalDateTime actionTime) {
        requireProcessable("REJECT");
        return complete(Status.REJECTED, "REJECTED", operatorUserId, actionTime);
    }

    public WorkflowTask withdraw(Long operatorUserId, LocalDateTime actionTime) {
        requireOpen();
        return complete(Status.CANCELLED, "WITHDRAWN", operatorUserId, actionTime);
    }

    private WorkflowTask complete(
            Status targetStatus, String result, Long operatorUserId, LocalDateTime actionTime) {
        Objects.requireNonNull(operatorUserId, "operatorUserId");
        Objects.requireNonNull(actionTime, "actionTime");
        if (operatorUserId <= 0) throw new IllegalArgumentException("operatorUserId must be positive");
        return new WorkflowTask(id, taskNo, instanceId, versionId, nodeId, nodeExecutionId,
                nodeCodeSnapshot, nodeNameSnapshot, taskRound, participantKey,
                assigneeUserId, candidateSnapshot, targetStatus, allowedActions,
                claimedTime, dueTime, operatorUserId, actionTime, result, version,
                assignmentMode);
    }

    private void requireProcessable(String action) {
        requireOpen();
        boolean allowed = java.util.Arrays.stream(allowedActions.split(","))
                .map(String::trim).anyMatch(action::equals);
        if (!allowed) throw new IllegalStateException("task does not allow action " + action);
    }

    private void requireOpen() {
        if (status != Status.PENDING && status != Status.CLAIMED) {
            throw new IllegalStateException("task in status " + status + " cannot be processed");
        }
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
