package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTaskAction;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowDefinitionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskActionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTransitionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeExecutionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionReleaseEntity;

final class WorkflowEntityMapper {
    private WorkflowEntityMapper() {}

    static WorkflowDefinition toDomain(WorkflowDefinitionEntity entity) {
        try {
            return new WorkflowDefinition(entity.getId(), entity.getDefinitionCode(), entity.getDefinitionName(),
                    entity.getBusinessType(), entity.getEnterpriseId(), entity.getOwnerOrgId(),
                    WorkflowDefinition.Status.valueOf(entity.getStatus()), entity.getCurrentVersionId(),
                    entity.getDescription(), entity.getVersion());
        } catch (RuntimeException exception) {
            throw corrupt("workflow_definition", exception);
        }
    }

    static WorkflowDefinitionEntity toEntity(WorkflowDefinition domain) {
        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
        entity.setId(domain.id());
        entity.setDefinitionCode(domain.definitionCode());
        entity.setDefinitionName(domain.definitionName());
        entity.setBusinessType(domain.businessType());
        entity.setEnterpriseId(domain.enterpriseId());
        entity.setOwnerOrgId(domain.ownerOrgId());
        entity.setStatus(domain.status().name());
        entity.setCurrentVersionId(domain.currentVersionId());
        entity.setDescription(domain.description());
        return entity;
    }

    static WorkflowVersion toDomain(WorkflowVersionEntity entity) {
        try {
            return new WorkflowVersion(entity.getId(), entity.getDefinitionId(), entity.getVersionNo(),
                    WorkflowVersion.Status.valueOf(entity.getStatus()), entity.getSchemaVersion(),
                    entity.getContentHash(), entity.getChangeNote(), entity.getEffectiveFrom(),
                    entity.getEffectiveTo(), entity.getPublishedBy(), entity.getPublishedTime(),
                    entity.getSourceVersionId(), WorkflowEngineMode.valueOf(entity.getEngineMode()),
                    WorkflowContentHashAlgorithm.valueOf(entity.getContentHashAlgorithm()),
                    ResolverBindingModel.valueOf(entity.getResolverBindingModel()),
                    entity.getResolverBindingManifestHash(), entity.getResolverBindingCount(),
                    entity.getResolverBindingCanonicalVersion(), entity.getVersion());
        } catch (RuntimeException exception) {
            throw corrupt("workflow_version", exception);
        }
    }

    static WorkflowVersionEntity toEntity(WorkflowVersion domain) {
        WorkflowVersionEntity entity = new WorkflowVersionEntity();
        entity.setId(domain.id());
        entity.setDefinitionId(domain.definitionId());
        entity.setVersionNo(domain.versionNo());
        entity.setStatus(domain.status().name());
        entity.setSchemaVersion(domain.schemaVersion());
        entity.setEngineMode(domain.engineMode().name());
        entity.setContentHashAlgorithm(domain.contentHashAlgorithm().name());
        entity.setResolverBindingModel(domain.resolverBindingModel().name());
        entity.setResolverBindingManifestHash(domain.resolverBindingManifestHash());
        entity.setResolverBindingCount(domain.resolverBindingCount());
        entity.setResolverBindingCanonicalVersion(domain.resolverBindingCanonicalVersion());
        entity.setContentHash(domain.contentHash());
        entity.setChangeNote(domain.changeNote());
        entity.setEffectiveFrom(domain.effectiveFrom());
        entity.setEffectiveTo(domain.effectiveTo());
        entity.setPublishedBy(domain.publishedBy());
        entity.setPublishedTime(domain.publishedTime());
        entity.setSourceVersionId(domain.sourceVersionId());
        return entity;
    }

    static WorkflowVersionRelease toDomain(WorkflowVersionReleaseEntity entity) {
        try {
            return new WorkflowVersionRelease(
                    entity.getId(), entity.getDefinitionId(), entity.getPreviousVersionId(),
                    entity.getPublishedVersionId(), entity.getPublishedVersionNo(), entity.getContentHash(),
                    entity.getOperatorUserId(), entity.getOperatorOrgId(), entity.getPublishedTime(),
                    entity.getTraceId(), entity.getValidationSummary(),
                    WorkflowEngineMode.valueOf(entity.getEngineMode()),
                    WorkflowContentHashAlgorithm.valueOf(entity.getContentHashAlgorithm()),
                    ResolverBindingModel.valueOf(entity.getResolverBindingModel()),
                    entity.getResolverBindingManifestHash(), entity.getResolverBindingCount(),
                    entity.getResolverBindingCanonicalVersion());
        } catch (RuntimeException exception) {
            throw corrupt("workflow_version_release", exception);
        }
    }

    static WorkflowVersionReleaseEntity toEntity(WorkflowVersionRelease domain) {
        WorkflowVersionReleaseEntity entity = new WorkflowVersionReleaseEntity();
        entity.setId(domain.id());
        entity.setDefinitionId(domain.definitionId());
        entity.setPreviousVersionId(domain.previousVersionId());
        entity.setPublishedVersionId(domain.publishedVersionId());
        entity.setPublishedVersionNo(domain.publishedVersionNo());
        entity.setContentHash(domain.contentHash());
        entity.setEngineMode(domain.engineMode().name());
        entity.setContentHashAlgorithm(domain.contentHashAlgorithm().name());
        entity.setResolverBindingModel(domain.resolverBindingModel().name());
        entity.setResolverBindingManifestHash(domain.resolverBindingManifestHash());
        entity.setResolverBindingCount(domain.resolverBindingCount());
        entity.setResolverBindingCanonicalVersion(domain.resolverBindingCanonicalVersion());
        entity.setOperatorUserId(domain.operatorUserId());
        entity.setOperatorOrgId(domain.operatorOrgId());
        entity.setPublishedTime(domain.publishedTime());
        entity.setTraceId(domain.traceId());
        entity.setValidationSummary(domain.validationSummary());
        return entity;
    }

    static WorkflowNode toDomain(WorkflowNodeEntity entity) {
        try {
            return new WorkflowNode(entity.getId(), entity.getVersionId(), entity.getNodeCode(),
                    entity.getNodeName(), WorkflowNode.NodeType.valueOf(entity.getNodeType()),
                    entity.getNodeOrder(), WorkflowNode.GovernanceNodeType.valueOf(entity.getGovernanceNodeType()),
                    WorkflowNode.ApprovalMode.valueOf(entity.getApprovalMode()), entity.getApprovalThreshold(),
                    WorkflowNode.AssignmentRuleType.valueOf(entity.getAssignmentRuleType()),
                    entity.getAssignmentRuleConfig(), entity.getEntryConditionConfig(),
                    entity.getCompletionConditionConfig(), entity.getTimeoutMinutes(),
                    Integer.valueOf(1).equals(entity.getWithdrawAllowed()),
                    Integer.valueOf(1).equals(entity.getEnabled()), entity.getVersion());
        } catch (RuntimeException exception) {
            throw corrupt("workflow_node", exception);
        }
    }

    static WorkflowNodeEntity toEntity(WorkflowNode domain) {
        WorkflowNodeEntity entity = new WorkflowNodeEntity();
        entity.setId(domain.id());
        entity.setVersionId(domain.versionId());
        entity.setNodeCode(domain.nodeCode());
        entity.setNodeName(domain.nodeName());
        entity.setNodeType(domain.nodeType().name());
        entity.setNodeOrder(domain.nodeOrder());
        entity.setGovernanceNodeType(domain.governanceNodeType().name());
        entity.setApprovalMode(domain.approvalMode().name());
        entity.setApprovalThreshold(domain.approvalThreshold());
        entity.setAssignmentRuleType(domain.assignmentRuleType().name());
        entity.setAssignmentRuleConfig(domain.assignmentRuleConfig());
        entity.setEntryConditionConfig(domain.entryConditionConfig());
        entity.setCompletionConditionConfig(domain.completionConditionConfig());
        entity.setTimeoutMinutes(domain.timeoutMinutes());
        entity.setWithdrawAllowed(domain.withdrawAllowed() ? 1 : 0);
        entity.setEnabled(domain.enabled() ? 1 : 0);
        return entity;
    }

    static WorkflowInstance toDomain(WorkflowInstanceEntity entity) {
        try {
            return new WorkflowInstance(entity.getId(), entity.getInstanceNo(), entity.getDefinitionId(),
                    entity.getVersionId(), entity.getDefinitionCodeSnapshot(), entity.getDefinitionVersionNo(),
                    entity.getDefinitionContentHashSnapshot(),
                    resolverBinding(entity),
                    entity.getBusinessType(), entity.getBusinessId(), entity.getBusinessKey(),
                    entity.getEnterpriseId(), entity.getSnapshotRef(), entity.getSnapshotHash(),
                    entity.getAttemptNo(), entity.getInitiatorUserId(), entity.getInitiatorOrgId(),
                    entity.getCurrentNodeId(), entity.getCurrentNodeExecutionId(),
                    WorkflowEngineMode.valueOf(entity.getEngineMode()),
                    WorkflowContentHashAlgorithm.valueOf(entity.getContentHashAlgorithmSnapshot()),
                    WorkflowInstance.Status.valueOf(entity.getStatus()),
                    entity.getResult(), entity.getVariablesSnapshot(), entity.getIdempotencyKey(),
                    entity.getRequestHash(), entity.getEventSequence(), entity.getTraceId(),
                    entity.getStartedTime(), entity.getCompletedTime(), entity.getWithdrawnTime(),
                    entity.getVersion());
        } catch (RuntimeException exception) {
            throw corrupt("workflow_instance", exception);
        }
    }

    static WorkflowInstanceEntity toEntity(WorkflowInstance domain) {
        WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
        entity.setId(domain.id());
        entity.setInstanceNo(domain.instanceNo());
        entity.setDefinitionId(domain.definitionId());
        entity.setVersionId(domain.versionId());
        entity.setDefinitionCodeSnapshot(domain.definitionCodeSnapshot());
        entity.setDefinitionVersionNo(domain.definitionVersionNo());
        entity.setDefinitionContentHashSnapshot(domain.definitionContentHashSnapshot());
        if (domain.resolverVersionBinding() != null) {
            entity.setResolverCode(domain.resolverVersionBinding().resolverCode().value());
            entity.setResolverVersion(domain.resolverVersionBinding().resolverVersion().value());
            entity.setResolverContractHash(domain.resolverVersionBinding().contractHash().value());
        }
        entity.setBusinessType(domain.businessType());
        entity.setBusinessId(domain.businessId());
        entity.setBusinessKey(domain.businessKey());
        entity.setEnterpriseId(domain.enterpriseId());
        entity.setSnapshotRef(domain.snapshotRef());
        entity.setSnapshotHash(domain.snapshotHash());
        entity.setAttemptNo(domain.attemptNo());
        entity.setInitiatorUserId(domain.initiatorUserId());
        entity.setInitiatorOrgId(domain.initiatorOrgId());
        entity.setCurrentNodeId(domain.currentNodeId());
        entity.setCurrentNodeExecutionId(domain.currentNodeExecutionId());
        entity.setEngineMode(domain.engineMode().name());
        entity.setContentHashAlgorithmSnapshot(domain.contentHashAlgorithmSnapshot().name());
        entity.setStatus(domain.status().name());
        entity.setResult(domain.result());
        entity.setVariablesSnapshot(domain.variablesSnapshot());
        entity.setIdempotencyKey(domain.idempotencyKey());
        entity.setRequestHash(domain.requestHash());
        entity.setEventSequence(domain.eventSequence());
        entity.setTraceId(domain.traceId());
        entity.setStartedTime(domain.startedTime());
        entity.setCompletedTime(domain.completedTime());
        entity.setWithdrawnTime(domain.withdrawnTime());
        entity.setVersion(domain.version());
        return entity;
    }

    private static ResolverVersionBinding resolverBinding(WorkflowInstanceEntity entity) {
        boolean allNull = entity.getResolverCode() == null && entity.getResolverVersion() == null
                && entity.getResolverContractHash() == null;
        if (allNull) return null;
        if (entity.getResolverCode() == null || entity.getResolverVersion() == null
                || entity.getResolverContractHash() == null) {
            throw new IllegalArgumentException("workflow instance resolver binding is incomplete");
        }
        return new ResolverVersionBinding(entity.getId(), ResolverCode.of(entity.getResolverCode()),
                ResolverVersion.of(entity.getResolverVersion()),
                ResolverContractHash.of(entity.getResolverContractHash()));
    }

    static WorkflowTask toDomain(WorkflowTaskEntity entity) {
        try {
            return new WorkflowTask(entity.getId(), entity.getTaskNo(), entity.getInstanceId(),
                    entity.getVersionId(), entity.getNodeId(), entity.getNodeExecutionId(), entity.getNodeCodeSnapshot(),
                    entity.getNodeNameSnapshot(), entity.getTaskRound(), entity.getParticipantKey(),
                    entity.getAssigneeUserId(), entity.getCandidateSnapshot(),
                    WorkflowTask.Status.valueOf(entity.getStatus()), entity.getAllowedActions(),
                    entity.getClaimedTime(), entity.getDueTime(), entity.getCompletedBy(),
                    entity.getCompletedTime(), entity.getDecisionResult(), entity.getVersion(),
                    entity.getAssignmentMode() == null
                            ? WorkflowTask.AssignmentMode.DIRECT
                            : WorkflowTask.AssignmentMode.valueOf(entity.getAssignmentMode()));
        } catch (RuntimeException exception) {
            throw corrupt("workflow_task", exception);
        }
    }

    static WorkflowTaskEntity toEntity(WorkflowTask domain) {
        WorkflowTaskEntity entity = new WorkflowTaskEntity();
        entity.setId(domain.id());
        entity.setTaskNo(domain.taskNo());
        entity.setInstanceId(domain.instanceId());
        entity.setVersionId(domain.versionId());
        entity.setNodeId(domain.nodeId());
        entity.setNodeExecutionId(domain.nodeExecutionId());
        entity.setNodeCodeSnapshot(domain.nodeCodeSnapshot());
        entity.setNodeNameSnapshot(domain.nodeNameSnapshot());
        entity.setTaskRound(domain.taskRound());
        entity.setParticipantKey(domain.participantKey());
        entity.setAssigneeUserId(domain.assigneeUserId());
        entity.setCandidateSnapshot(domain.candidateSnapshot());
        entity.setAssignmentMode(domain.assignmentMode().name());
        entity.setStatus(domain.status().name());
        entity.setAllowedActions(domain.allowedActions());
        entity.setClaimedTime(domain.claimedTime());
        entity.setDueTime(domain.dueTime());
        entity.setCompletedBy(domain.completedBy());
        entity.setCompletedTime(domain.completedTime());
        entity.setDecisionResult(domain.decisionResult());
        entity.setVersion(domain.version());
        return entity;
    }

    static WorkflowTaskAction toDomain(WorkflowTaskActionEntity entity) {
        try {
            return new WorkflowTaskAction(entity.getId(), entity.getActionNo(), entity.getTaskId(),
                    entity.getInstanceId(), WorkflowTaskAction.ActionType.valueOf(entity.getActionType()),
                    entity.getOperatorUserId(), entity.getOperatorOrgId(), entity.getActionComment(),
                    entity.getActionTime(), entity.getIdempotencyKey(), entity.getRequestHash(),
                    entity.getTraceId(), entity.getVersion());
        } catch (RuntimeException exception) {
            throw corrupt("workflow_task_action", exception);
        }
    }

    static WorkflowTaskActionEntity toEntity(WorkflowTaskAction domain) {
        WorkflowTaskActionEntity entity = new WorkflowTaskActionEntity();
        entity.setId(domain.id());
        entity.setActionNo(domain.actionNo());
        entity.setTaskId(domain.taskId());
        entity.setInstanceId(domain.instanceId());
        entity.setActionType(domain.actionType().name());
        entity.setOperatorUserId(domain.operatorUserId());
        entity.setOperatorOrgId(domain.operatorOrgId());
        entity.setActionComment(domain.actionComment());
        entity.setActionTime(domain.actionTime());
        entity.setIdempotencyKey(domain.idempotencyKey());
        entity.setRequestHash(domain.requestHash());
        entity.setTraceId(domain.traceId());
        entity.setVersion(domain.version());
        return entity;
    }

    static WorkflowTransition toDomain(WorkflowTransitionEntity entity) {
        try {
            return new WorkflowTransition(entity.getId(), entity.getVersionId(), entity.getTransitionCode(),
                    entity.getTransitionName(), entity.getFromNodeId(), entity.getToNodeId(),
                    WorkflowTransition.TriggerType.valueOf(entity.getTriggerType()),
                    WorkflowTransition.RouteType.valueOf(entity.getRouteType()), entity.getPriority(),
                    entity.getConditionConfig(), Integer.valueOf(1).equals(entity.getEnabled()),
                    entity.getVersion());
        } catch (RuntimeException exception) {
            throw corrupt("workflow_transition", exception);
        }
    }

    static WorkflowTransitionEntity toEntity(WorkflowTransition domain) {
        WorkflowTransitionEntity entity = new WorkflowTransitionEntity();
        entity.setId(domain.id());
        entity.setVersionId(domain.versionId());
        entity.setTransitionCode(domain.transitionCode());
        entity.setTransitionName(domain.transitionName());
        entity.setFromNodeId(domain.fromNodeId());
        entity.setToNodeId(domain.toNodeId());
        entity.setTriggerType(domain.triggerType().name());
        entity.setRouteType(domain.routeType().name());
        entity.setPriority(domain.priority());
        entity.setConditionConfig(domain.conditionConfig());
        entity.setEnabled(domain.enabled() ? 1 : 0);
        return entity;
    }

    static WorkflowNodeExecution toDomain(WorkflowNodeExecutionEntity entity) {
        try {
            return new WorkflowNodeExecution(entity.getId(), entity.getExecutionNo(), entity.getInstanceId(),
                    entity.getVersionId(), entity.getNodeId(), entity.getNodeCodeSnapshot(),
                    entity.getNodeNameSnapshot(), entity.getVisitNo(), entity.getPreviousExecutionId(),
                    entity.getSourceTransitionId(), WorkflowNodeExecution.Status.valueOf(entity.getStatus()),
                    entity.getResult(), entity.getEnteredTime(), entity.getActivatedTime(),
                    entity.getCompletedBy(), entity.getCompletedTime(), entity.getFailureCode(),
                    entity.getTraceId(), entity.getVersion());
        } catch (RuntimeException exception) {
            throw corrupt("workflow_node_execution", exception);
        }
    }

    static WorkflowNodeExecutionEntity toEntity(WorkflowNodeExecution domain) {
        WorkflowNodeExecutionEntity entity = new WorkflowNodeExecutionEntity();
        entity.setId(domain.id());
        entity.setExecutionNo(domain.executionNo());
        entity.setInstanceId(domain.instanceId());
        entity.setVersionId(domain.versionId());
        entity.setNodeId(domain.nodeId());
        entity.setNodeCodeSnapshot(domain.nodeCodeSnapshot());
        entity.setNodeNameSnapshot(domain.nodeNameSnapshot());
        entity.setVisitNo(domain.visitNo());
        entity.setPreviousExecutionId(domain.previousExecutionId());
        entity.setSourceTransitionId(domain.sourceTransitionId());
        entity.setStatus(domain.status().name());
        entity.setResult(domain.result());
        entity.setEnteredTime(domain.enteredTime());
        entity.setActivatedTime(domain.activatedTime());
        entity.setCompletedBy(domain.completedBy());
        entity.setCompletedTime(domain.completedTime());
        entity.setFailureCode(domain.failureCode());
        entity.setTraceId(domain.traceId());
        entity.setVersion(domain.version());
        return entity;
    }

    private static BusinessException corrupt(String table, RuntimeException cause) {
        BusinessException exception = new BusinessException("B2500", table + " data violates workflow domain rules");
        exception.initCause(cause);
        return exception;
    }
}
