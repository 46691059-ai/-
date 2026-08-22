package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionStatus;
import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.*;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class RoleRuntimeExecutionAdmissionEntityMapper {
    private RoleRuntimeExecutionAdmissionEntityMapper() { }
    private static LocalDateTime local(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }

    static WorkflowRoleRuntimeExecutionAdmissionEntity toEntity(PersistentRoleRuntimeExecutionAdmission d) {
        var e = new WorkflowRoleRuntimeExecutionAdmissionEntity(); e.setId(d.id());
        e.setAdmissionId(d.admissionId()); e.setRequestId(d.requestId()); e.setIdempotencyKey(d.idempotencyKey());
        e.setCandidateSnapshotRowId(d.candidateSnapshotRowId()); e.setSnapshotId(d.snapshotId());
        e.setPromotionId(d.promotionId()); e.setActivationId(d.activationId()); e.setSourceDeleteToken(0L);
        e.setActivationHash(d.activationHash()); e.setPromotionHash(d.promotionHash()); e.setBindingHash(d.bindingHash());
        e.setCandidateHash(d.candidateHash()); e.setExecutionAdmissionHash(d.executionAdmissionHash());
        e.setCapabilityEvidenceRootHash(d.capabilityEvidenceRootHash()); e.setPersistenceHash(d.persistenceHash());
        e.setResolverCode(d.resolverCode()); e.setResolverVersion(d.resolverVersion()); e.setResolverContractHash(d.resolverContractHash());
        e.setDirectoryRevision(d.directoryRevision()); e.setDirectoryResultHash(d.directoryResultHash());
        e.setDirectoryFenceTokenHash(d.directoryFenceTokenHash()); e.setDirectoryFenceExpiresAt(local(d.directoryFenceExpiresAt()));
        e.setDirectoryVerifiedAt(local(d.directoryVerifiedAt())); e.setEnterpriseId(d.enterpriseId()); e.setBusinessScope(d.businessScope());
        e.setDefinitionReleaseId(d.definitionReleaseId()); e.setDefinitionId(d.definitionId());
        e.setDefinitionVersionId(d.definitionVersionId()); e.setNodeId(d.nodeId()); e.setNodeBindingHash(d.nodeBindingHash()); e.setGraphHash(d.graphHash());
        e.setDecision(d.decision().name()); e.setPolicyVersion(d.policyVersion()); e.setEffectiveAt(local(d.effectiveAt()));
        e.setAdmissionExpiresAt(local(d.admissionExpiresAt())); e.setExecutedCheckCount(d.executedCheckCount());
        e.setLastCheckSequence(d.lastCheckSequence()); e.setFeatureFlagEvidenceHash(d.featureFlagEvidenceHash());
        e.setCanaryEvidenceHash(d.canaryEvidenceHash()); e.setKillSwitchEvidenceHash(d.killSwitchEvidenceHash());
        e.setRequestedBy(d.requestedBy()); e.setDecidedBy(d.decidedBy()); e.setDecidedAt(local(d.decidedAt())); return e;
    }

    static PersistentRoleRuntimeExecutionAdmission toDomain(WorkflowRoleRuntimeExecutionAdmissionEntity e) {
        return new PersistentRoleRuntimeExecutionAdmission(e.getId(), e.getAdmissionId(), e.getRequestId(), e.getIdempotencyKey(),
                e.getCandidateSnapshotRowId(), e.getSnapshotId(), e.getPromotionId(), e.getActivationId(), e.getActivationHash(),
                e.getPromotionHash(), e.getBindingHash(), e.getCandidateHash(), e.getExecutionAdmissionHash(),
                e.getCapabilityEvidenceRootHash(), e.getPersistenceHash(), e.getResolverCode(), e.getResolverVersion(),
                e.getResolverContractHash(), e.getDirectoryRevision(), e.getDirectoryResultHash(), e.getDirectoryFenceTokenHash(),
                instant(e.getDirectoryFenceExpiresAt()), instant(e.getDirectoryVerifiedAt()), e.getEnterpriseId(), e.getBusinessScope(),
                e.getDefinitionReleaseId(), e.getDefinitionId(), e.getDefinitionVersionId(), e.getNodeId(), e.getNodeBindingHash(),
                e.getGraphHash(), RoleRuntimeExecutionAdmissionStatus.valueOf(e.getDecision()), e.getPolicyVersion(),
                instant(e.getEffectiveAt()), instant(e.getAdmissionExpiresAt()), e.getExecutedCheckCount(), e.getLastCheckSequence(),
                e.getFeatureFlagEvidenceHash(), e.getCanaryEvidenceHash(), e.getKillSwitchEvidenceHash(), e.getRequestedBy(),
                e.getDecidedBy(), instant(e.getDecidedAt()));
    }

    static WorkflowRoleRuntimeExecutionAdmissionEvidenceEntity toEntity(RoleRuntimeExecutionAdmissionEvidenceRecord d) {
        var e = new WorkflowRoleRuntimeExecutionAdmissionEvidenceEntity(); e.setId(d.id()); e.setAdmissionRowId(d.admissionRowId());
        e.setAdmissionId(d.admissionId()); e.setSequenceNo(d.sequenceNo()); e.setValidatorCode(d.validatorCode());
        e.setEvidenceType(d.evidenceType()); e.setResult(d.result()); e.setBlockReason(d.blockReason());
        e.setCapabilityCode(d.capabilityCode()); e.setCapabilityStatus(d.capabilityStatus()); e.setProviderVersion(d.providerVersion());
        e.setPolicyVersion(d.policyVersion()); e.setObservedValueCode(d.observedValueCode()); e.setScopeType(d.scopeType());
        e.setScopeEnterpriseId(d.scopeEnterpriseId()); e.setScopeDefinitionId(d.scopeDefinitionId());
        e.setScopeDefinitionVersionId(d.scopeDefinitionVersionId()); e.setScopeNodeId(d.scopeNodeId());
        e.setCheckedAt(local(d.checkedAt())); e.setSubjectHash(d.subjectHash()); e.setEvidenceHash(d.evidenceHash());
        e.setCanonicalVersion(d.canonicalVersion()); return e;
    }

    static RoleRuntimeExecutionAdmissionEvidenceRecord toDomain(WorkflowRoleRuntimeExecutionAdmissionEvidenceEntity e) {
        return new RoleRuntimeExecutionAdmissionEvidenceRecord(e.getId(), e.getAdmissionRowId(), e.getAdmissionId(), e.getSequenceNo(),
                e.getValidatorCode(), e.getEvidenceType(), e.getResult(), e.getBlockReason(), e.getCapabilityCode(),
                e.getCapabilityStatus(), e.getProviderVersion(), e.getPolicyVersion(), e.getObservedValueCode(), e.getScopeType(),
                e.getScopeEnterpriseId(), e.getScopeDefinitionId(), e.getScopeDefinitionVersionId(), e.getScopeNodeId(),
                instant(e.getCheckedAt()), e.getSubjectHash(), e.getEvidenceHash(), e.getCanonicalVersion());
    }

    static WorkflowRoleRuntimeExecutionAdmissionEventEntity toEntity(RoleRuntimeExecutionAdmissionEvent d) {
        var e = new WorkflowRoleRuntimeExecutionAdmissionEventEntity(); e.setId(d.id()); e.setAdmissionRowId(d.admissionRowId());
        e.setAdmissionId(d.admissionId()); e.setCandidateSnapshotRowId(d.candidateSnapshotRowId()); e.setSequenceNo(d.sequenceNo());
        e.setEventType(d.eventType().name()); e.setFromStatus(d.fromStatus() == null ? null : d.fromStatus().name());
        e.setToStatus(d.toStatus().name()); e.setReasonCode(d.reasonCode()); e.setSourceEvidenceRootHash(d.sourceEvidenceRootHash());
        e.setSourcePersistenceHash(d.sourcePersistenceHash());
        e.setPreviousEventHash(d.previousEventHash()); e.setEventHash(d.eventHash()); e.setOccurredAt(local(d.occurredAt()));
        e.setOperatorId(d.operatorId()); e.setOperatorRole(d.operatorRole()); e.setIdempotencyKey(d.idempotencyKey());
        e.setCanonicalVersion(d.canonicalVersion()); return e;
    }

    static RoleRuntimeExecutionAdmissionEvent toDomain(WorkflowRoleRuntimeExecutionAdmissionEventEntity e) {
        return new RoleRuntimeExecutionAdmissionEvent(e.getId(), e.getAdmissionRowId(), e.getAdmissionId(), e.getCandidateSnapshotRowId(),
                e.getSequenceNo(), RoleRuntimeExecutionAdmissionEventType.valueOf(e.getEventType()),
                e.getFromStatus() == null ? null : RoleRuntimeExecutionAdmissionStatus.valueOf(e.getFromStatus()),
                RoleRuntimeExecutionAdmissionStatus.valueOf(e.getToStatus()), e.getReasonCode(), e.getSourceEvidenceRootHash(),
                e.getSourcePersistenceHash(), e.getPreviousEventHash(), e.getEventHash(), instant(e.getOccurredAt()), e.getOperatorId(), e.getOperatorRole(),
                e.getIdempotencyKey(), e.getCanonicalVersion());
    }

    static RoleRuntimeExecutionAdmissionSlot toDomain(WorkflowRoleRuntimeExecutionAdmissionSlotEntity e) {
        return new RoleRuntimeExecutionAdmissionSlot(e.getCandidateSnapshotRowId(), e.getSnapshotId(), e.getActiveAdmissionRowId(),
                e.getActiveAdmissionId(), e.getActiveToken(), e.getSlotStatus(), e.getVersion());
    }
}
