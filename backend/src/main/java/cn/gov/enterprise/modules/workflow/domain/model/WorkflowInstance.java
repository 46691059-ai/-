package cn.gov.enterprise.modules.workflow.domain.model;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;
import java.time.LocalDateTime;
import java.util.Objects;

/** One immutable-definition execution context for Workflow Lite. */
public record WorkflowInstance(
        Long id, String instanceNo, Long definitionId, Long versionId,
        String definitionCodeSnapshot, int definitionVersionNo, String definitionContentHashSnapshot,
        ResolverVersionBinding resolverVersionBinding,
        String businessType, String businessId, String businessKey, Long enterpriseId,
        String snapshotRef, String snapshotHash, int attemptNo,
        Long initiatorUserId, Long initiatorOrgId, Long currentNodeId,
        Long currentNodeExecutionId, WorkflowEngineMode engineMode,
        WorkflowContentHashAlgorithm contentHashAlgorithmSnapshot,
        Status status, String result, String variablesSnapshot,
        String idempotencyKey, String requestHash, long eventSequence, String traceId,
        LocalDateTime startedTime, LocalDateTime completedTime, LocalDateTime withdrawnTime,
        int version) {

    public enum Status { CREATED, RUNNING, APPROVED, REJECTED, WITHDRAWN, COMPLETED, EXCEPTION }

    public WorkflowInstance {
        Objects.requireNonNull(id, "id");
        instanceNo = required(instanceNo, "instanceNo", 100);
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(versionId, "versionId");
        definitionCodeSnapshot = required(definitionCodeSnapshot, "definitionCodeSnapshot", 100);
        if (definitionVersionNo <= 0) throw new IllegalArgumentException("definitionVersionNo must be positive");
        definitionContentHashSnapshot = required(
                definitionContentHashSnapshot, "definitionContentHashSnapshot", 128);
        businessType = required(businessType, "businessType", 64);
        businessId = required(businessId, "businessId", 100);
        businessKey = required(businessKey, "businessKey", 200);
        Objects.requireNonNull(enterpriseId, "enterpriseId");
        if (attemptNo <= 0) throw new IllegalArgumentException("attemptNo must be positive");
        Objects.requireNonNull(initiatorUserId, "initiatorUserId");
        Objects.requireNonNull(initiatorOrgId, "initiatorOrgId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(engineMode, "engineMode");
        Objects.requireNonNull(contentHashAlgorithmSnapshot, "contentHashAlgorithmSnapshot");
        if (engineMode == WorkflowEngineMode.SINGLE_NODE_LEGACY && currentNodeExecutionId != null) {
            throw new IllegalArgumentException("legacy workflow cannot reference a node execution");
        }
        if (resolverVersionBinding != null && !id.equals(resolverVersionBinding.instanceId())) {
            throw new IllegalArgumentException("resolver binding belongs to another instance");
        }
        idempotencyKey = required(idempotencyKey, "idempotencyKey", 200);
        requestHash = required(requestHash, "requestHash", 128);
        if (eventSequence < 0) throw new IllegalArgumentException("eventSequence must not be negative");
        if (status != Status.CREATED && startedTime == null) {
            throw new IllegalArgumentException("started workflow instance requires startedTime");
        }
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    /** Compatibility constructor for records materialized before V2.6.1. */
    public WorkflowInstance(
            Long id, String instanceNo, Long definitionId, Long versionId,
            String definitionCodeSnapshot, int definitionVersionNo,
            String definitionContentHashSnapshot, String businessType, String businessId,
            String businessKey, Long enterpriseId, String snapshotRef, String snapshotHash,
            int attemptNo, Long initiatorUserId, Long initiatorOrgId, Long currentNodeId,
            Status status, String result, String variablesSnapshot, String idempotencyKey,
            String requestHash, long eventSequence, String traceId, LocalDateTime startedTime,
            LocalDateTime completedTime, LocalDateTime withdrawnTime, int version) {
        this(id, instanceNo, definitionId, versionId, definitionCodeSnapshot,
                definitionVersionNo, definitionContentHashSnapshot, null, businessType, businessId,
                businessKey, enterpriseId, snapshotRef, snapshotHash, attemptNo,
                initiatorUserId, initiatorOrgId, currentNodeId, null,
                WorkflowEngineMode.SINGLE_NODE_LEGACY,
                WorkflowContentHashAlgorithm.NODE_V1_SHA256, status, result,
                variablesSnapshot, idempotencyKey, requestHash, eventSequence, traceId,
                startedTime, completedTime, withdrawnTime, version);
    }

    public static WorkflowInstance running(
            Long id, String instanceNo, WorkflowDefinition definition, WorkflowVersion workflowVersion,
            String businessType, String businessId, String businessKey, Long enterpriseId,
            String snapshotRef, String snapshotHash, int attemptNo, Long initiatorUserId,
            Long initiatorOrgId, Long currentNodeId, String variablesSnapshot,
            String idempotencyKey, String requestHash, String traceId, LocalDateTime startedTime) {
        return new WorkflowInstance(id, instanceNo, definition.id(), workflowVersion.id(),
                definition.definitionCode(), workflowVersion.versionNo(), workflowVersion.contentHash(),
                null, businessType, businessId,
                businessKey, enterpriseId, snapshotRef, snapshotHash, attemptNo, initiatorUserId,
                initiatorOrgId, currentNodeId, null, WorkflowEngineMode.SINGLE_NODE_LEGACY,
                WorkflowContentHashAlgorithm.NODE_V1_SHA256, Status.RUNNING, null, variablesSnapshot,
                idempotencyKey, requestHash, 0, traceId, startedTime, null, null, 0);
    }

    public static WorkflowInstance runningLinear(
            Long id, String instanceNo, WorkflowDefinition definition, WorkflowVersion workflowVersion,
            String businessType, String businessId, String businessKey, Long enterpriseId,
            String snapshotRef, String snapshotHash, int attemptNo, Long initiatorUserId,
            Long initiatorOrgId, Long entryNodeId, String variablesSnapshot,
            String idempotencyKey, String requestHash, String traceId, LocalDateTime startedTime) {
        if (workflowVersion.engineMode() != WorkflowEngineMode.MULTI_NODE_LINEAR_V1
                || workflowVersion.contentHashAlgorithm()
                != WorkflowContentHashAlgorithm.GRAPH_V2_SHA256) {
            throw new IllegalArgumentException("workflow version is not a linear graph version");
        }
        return new WorkflowInstance(id, instanceNo, definition.id(), workflowVersion.id(),
                definition.definitionCode(), workflowVersion.versionNo(), workflowVersion.contentHash(),
                null, businessType, businessId, businessKey, enterpriseId, snapshotRef, snapshotHash,
                attemptNo, initiatorUserId, initiatorOrgId, entryNodeId, null,
                workflowVersion.engineMode(), workflowVersion.contentHashAlgorithm(),
                Status.RUNNING, null, variablesSnapshot, idempotencyKey, requestHash, 0,
                traceId, startedTime, null, null, 0);
    }

    public static WorkflowInstance runningLinear(
            Long id, String instanceNo, WorkflowDefinition definition, WorkflowVersion workflowVersion,
            ResolverVersionBinding resolverVersionBinding,
            String businessType, String businessId, String businessKey, Long enterpriseId,
            String snapshotRef, String snapshotHash, int attemptNo, Long initiatorUserId,
            Long initiatorOrgId, Long entryNodeId, String variablesSnapshot,
            String idempotencyKey, String requestHash, String traceId, LocalDateTime startedTime) {
        if (workflowVersion.engineMode() != WorkflowEngineMode.MULTI_NODE_LINEAR_V1
                || workflowVersion.contentHashAlgorithm()
                != WorkflowContentHashAlgorithm.GRAPH_V2_SHA256) {
            throw new IllegalArgumentException("workflow version is not a linear graph version");
        }
        Objects.requireNonNull(resolverVersionBinding, "resolverVersionBinding");
        return new WorkflowInstance(id, instanceNo, definition.id(), workflowVersion.id(),
                definition.definitionCode(), workflowVersion.versionNo(), workflowVersion.contentHash(),
                resolverVersionBinding, businessType, businessId, businessKey, enterpriseId,
                snapshotRef, snapshotHash, attemptNo, initiatorUserId, initiatorOrgId, entryNodeId,
                null, workflowVersion.engineMode(), workflowVersion.contentHashAlgorithm(),
                Status.RUNNING, null, variablesSnapshot, idempotencyKey, requestHash, 0,
                traceId, startedTime, null, null, 0);
    }

    public ResolverVersionBinding requireResolverVersionBinding() {
        if (resolverVersionBinding == null) {
            throw new IllegalStateException("workflow instance has no frozen resolver version binding");
        }
        return resolverVersionBinding;
    }

    public WorkflowInstance pointTo(Long nodeId, Long executionId) {
        requireLinearRunning();
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(executionId, "executionId");
        return copy(nodeId, executionId, Status.RUNNING, null, null, null, eventSequence + 1);
    }

    public WorkflowInstance completeLinear(LocalDateTime actionTime) {
        requireLinearRunning();
        Objects.requireNonNull(actionTime, "actionTime");
        return copy(null, null, Status.APPROVED, "APPROVED", actionTime, null, eventSequence + 1);
    }

    public WorkflowInstance rejectLinear(LocalDateTime actionTime) {
        requireLinearRunning();
        Objects.requireNonNull(actionTime, "actionTime");
        return copy(null, null, Status.REJECTED, "REJECTED", actionTime, null, eventSequence + 1);
    }

    public WorkflowInstance approve(LocalDateTime actionTime) {
        return finish(Status.APPROVED, "APPROVED", actionTime, null);
    }

    public WorkflowInstance reject(LocalDateTime actionTime) {
        return finish(Status.REJECTED, "REJECTED", actionTime, null);
    }

    public WorkflowInstance withdraw(LocalDateTime actionTime) {
        return finish(Status.WITHDRAWN, "WITHDRAWN", null, actionTime);
    }

    private WorkflowInstance finish(
            Status targetStatus, String targetResult,
            LocalDateTime targetCompletedTime, LocalDateTime targetWithdrawnTime) {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("workflow instance in status " + status + " cannot be processed");
        }
        LocalDateTime eventTime = targetCompletedTime == null ? targetWithdrawnTime : targetCompletedTime;
        Objects.requireNonNull(eventTime, "actionTime");
        return new WorkflowInstance(id, instanceNo, definitionId, versionId,
                definitionCodeSnapshot, definitionVersionNo, definitionContentHashSnapshot,
                resolverVersionBinding, businessType, businessId,
                businessKey, enterpriseId, snapshotRef, snapshotHash, attemptNo,
                initiatorUserId, initiatorOrgId, currentNodeId, currentNodeExecutionId,
                engineMode, contentHashAlgorithmSnapshot, targetStatus, targetResult,
                variablesSnapshot, idempotencyKey, requestHash, eventSequence + 1, traceId,
                startedTime, targetCompletedTime, targetWithdrawnTime, version);
    }

    private WorkflowInstance copy(
            Long nodeId, Long executionId, Status targetStatus, String targetResult,
            LocalDateTime targetCompletedTime, LocalDateTime targetWithdrawnTime,
            long targetEventSequence) {
        return new WorkflowInstance(id, instanceNo, definitionId, versionId,
                definitionCodeSnapshot, definitionVersionNo, definitionContentHashSnapshot,
                resolverVersionBinding, businessType, businessId, businessKey, enterpriseId,
                snapshotRef, snapshotHash, attemptNo, initiatorUserId, initiatorOrgId,
                nodeId, executionId, engineMode,
                contentHashAlgorithmSnapshot, targetStatus, targetResult, variablesSnapshot,
                idempotencyKey, requestHash, targetEventSequence, traceId, startedTime,
                targetCompletedTime, targetWithdrawnTime, version);
    }

    private void requireLinearRunning() {
        if (engineMode != WorkflowEngineMode.MULTI_NODE_LINEAR_V1 || status != Status.RUNNING) {
            throw new IllegalStateException("workflow instance is not a running linear instance");
        }
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
