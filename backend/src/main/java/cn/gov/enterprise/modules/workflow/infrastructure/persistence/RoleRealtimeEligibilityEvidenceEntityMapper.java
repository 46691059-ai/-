package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.RoleRealtimeEligibilityPersistenceBundle;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.RoleRealtimeEligibilityPersistenceCanonical;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class RoleRealtimeEligibilityEvidenceEntityMapper {
    private RoleRealtimeEligibilityEvidenceEntityMapper() { }
    private static LocalDateTime local(Instant v) { return v == null ? null : LocalDateTime.ofInstant(v, ZoneOffset.UTC); }
    private static Instant instant(LocalDateTime v) { return v == null ? null : v.toInstant(ZoneOffset.UTC); }

    static WorkflowRoleRealtimeEligibilityEvidenceEntity header(RoleRealtimeEligibilityPersistenceBundle.Header d) {
        var e=new WorkflowRoleRealtimeEligibilityEvidenceEntity(); e.setId(d.id());
        e.setEligibilityEvidenceId(d.evidenceId()); e.setEligibilityRequestId(d.eligibilityRequestId());
        e.setClaimRequestId(d.claimRequestId()); e.setClaimIdempotencyKey(d.claimIdempotencyKey());
        e.setAttemptNo(d.attemptNo()); e.setCorrelationId(d.correlationId()); e.setInstanceId(d.instanceId());
        e.setDefinitionVersionId(d.definitionVersionId()); e.setNodeId(d.nodeId()); e.setNodeExecutionId(d.nodeExecutionId());
        e.setTaskId(d.taskId()); e.setCandidatePoolId(d.candidatePoolId()); e.setCandidateMemberId(d.candidateMemberId());
        e.setCandidateUserId(d.candidateUserId()); e.setBindingSetId(d.bindingSetId());
        e.setResolverBindingId(d.resolverBindingId()); e.setNodeResolverBindingId(d.nodeResolverBindingId());
        e.setRoleCode(d.roleCode()); e.setOrganizationId(d.organizationId()); e.setCandidatePoolHash(d.candidatePoolHash());
        e.setRuntimeBindingHash(d.runtimeBindingHash()); e.setEligibilityHash(d.eligibilityHash());
        e.setCandidateDirectoryRevision(d.candidateDirectoryRevision()); e.setClaimDirectoryRevision(d.claimDirectoryRevision());
        e.setDirectoryResultHash(d.directoryResultHash()); e.setDirectoryContractHash(d.directoryContractHash());
        e.setDirectoryComplete(d.directoryComplete()?1:0); e.setDirectoryEffectiveAt(local(d.directoryEffectiveAt()));
        e.setDirectoryCheckedAt(local(d.directoryCheckedAt())); e.setDecision(d.decision());
        e.setTerminalValidatorOrder(d.terminalValidatorOrder()); e.setValidatorCount(d.validatorCount());
        e.setCapabilityCount(d.capabilityCount()); e.setValidatorEvidenceRootHash(d.validatorRootHash());
        e.setCapabilityEvidenceRootHash(d.capabilityRootHash()); e.setPersistenceHash(d.persistenceHash());
        e.setEligibilityCanonicalVersion(RoleRealtimeEligibilityPersistenceCanonical.ELIGIBILITY_VERSION);
        e.setPersistenceCanonicalVersion(RoleRealtimeEligibilityPersistenceCanonical.PERSISTENCE_VERSION);
        e.setPolicyVersion(d.policyVersion()); e.setEvidenceSource(d.evidenceSource()); e.setClaimAt(local(d.claimAt()));
        e.setVerifiedAt(local(d.verifiedAt())); e.setExpiresAt(local(d.expiresAt())); return e;
    }

    static RoleRealtimeEligibilityPersistenceBundle.Header header(WorkflowRoleRealtimeEligibilityEvidenceEntity e) {
        return new RoleRealtimeEligibilityPersistenceBundle.Header(e.getId(),e.getEligibilityEvidenceId(),e.getEligibilityRequestId(),
                e.getClaimRequestId(),e.getClaimIdempotencyKey(),e.getAttemptNo(),e.getCorrelationId(),e.getInstanceId(),
                e.getDefinitionVersionId(),e.getNodeId(),e.getNodeExecutionId(),e.getTaskId(),e.getCandidatePoolId(),
                e.getCandidateMemberId(),e.getCandidateUserId(),e.getBindingSetId(),e.getResolverBindingId(),
                e.getNodeResolverBindingId(),e.getRoleCode(),e.getOrganizationId(),e.getCandidatePoolHash(),
                e.getRuntimeBindingHash(),e.getEligibilityHash(),e.getCandidateDirectoryRevision(),e.getClaimDirectoryRevision(),
                e.getDirectoryResultHash(),e.getDirectoryContractHash(),e.getDirectoryComplete()==1,instant(e.getDirectoryEffectiveAt()),
                instant(e.getDirectoryCheckedAt()),e.getDecision(),e.getTerminalValidatorOrder(),e.getValidatorCount(),
                e.getCapabilityCount(),e.getValidatorEvidenceRootHash(),e.getCapabilityEvidenceRootHash(),e.getPersistenceHash(),
                e.getPolicyVersion(),e.getEvidenceSource(),instant(e.getClaimAt()),instant(e.getVerifiedAt()),instant(e.getExpiresAt()));
    }

    static WorkflowRoleRealtimeEligibilityValidatorEvidenceEntity validator(RoleRealtimeEligibilityPersistenceBundle.ValidatorEvidence d) {
        var e=new WorkflowRoleRealtimeEligibilityValidatorEvidenceEntity(); e.setId(d.id()); e.setEvidenceId(d.evidenceRowId());
        e.setValidatorCode(d.code()); e.setValidatorOrder(d.order()); e.setStatus(d.status()); e.setReasonCode(d.reasonCode());
        e.setEvidenceHash(d.evidenceHash()); e.setCheckedAt(local(d.checkedAt()));
        e.setCanonicalVersion(RoleRealtimeEligibilityPersistenceCanonical.ELIGIBILITY_VERSION); return e;
    }
    static WorkflowRoleRealtimeEligibilityCapabilityEvidenceEntity capability(RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence d) {
        var e=new WorkflowRoleRealtimeEligibilityCapabilityEvidenceEntity(); e.setId(d.id()); e.setEvidenceId(d.evidenceRowId());
        e.setCapabilityCode(d.capabilityCode()); e.setValidatorCode(d.validatorCode()); e.setStatus(d.status());
        e.setDecision(d.decision()); e.setProviderVersion(d.providerVersion()); e.setPolicyVersion(d.policyVersion());
        e.setEvidenceHash(d.evidenceHash()); e.setCheckedAt(local(d.checkedAt())); e.setValidUntil(local(d.validUntil()));
        e.setCanonicalVersion(RoleRealtimeEligibilityPersistenceCanonical.ELIGIBILITY_VERSION); return e;
    }
    static WorkflowRoleRealtimeEligibilityEventEntity event(RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent d) {
        var e=new WorkflowRoleRealtimeEligibilityEventEntity(); e.setId(d.id()); e.setEvidenceId(d.evidenceRowId());
        e.setSequenceNo(d.sequenceNo()); e.setEventType(d.eventType()); e.setClaimId(d.claimId()); e.setReasonCode(d.reasonCode());
        e.setPreviousEventHash(d.previousEventHash()); e.setEventHash(d.eventHash()); e.setOccurredAt(local(d.occurredAt()));
        e.setOperatorId(d.operatorId()); e.setIdempotencyKey(d.idempotencyKey());
        e.setCanonicalVersion(RoleRealtimeEligibilityPersistenceCanonical.PERSISTENCE_VERSION); return e;
    }

    static RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent event(WorkflowRoleRealtimeEligibilityEventEntity e) {
        return new RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent(e.getId(), e.getEvidenceId(),
                e.getSequenceNo(), e.getEventType(), e.getClaimId(), e.getReasonCode(),
                e.getPreviousEventHash(), e.getEventHash(), instant(e.getOccurredAt()),
                e.getOperatorId(), e.getIdempotencyKey());
    }
}
