package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.role.ActivationEvidenceRecord;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationEvidenceType;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationDecision;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationRequest;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationStatus;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.RoleRuntimeActivationApprovalEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.RoleRuntimeActivationEvidenceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.RoleRuntimeActivationRequestEntity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class ActivationPersistenceEntityMapper {
    private ActivationPersistenceEntityMapper() { }

    static RoleRuntimeActivationRequestEntity toEntity(PersistentActivationRequest value) {
        var entity = new RoleRuntimeActivationRequestEntity();
        entity.setId(value.id()); entity.setActivationId(value.activationId());
        entity.setResolverCode(value.resolverCode()); entity.setResolverVersion(value.resolverVersion());
        entity.setContractHash(value.contractHash()); entity.setBindingHash(value.bindingHash());
        entity.setCandidateHash(value.candidateHash()); entity.setDirectoryContractHash(value.directoryContractHash());
        entity.setDirectoryRevision(value.directoryRevision()); entity.setActivationHash(value.activationHash());
        entity.setApprovalEvidenceHash(value.approvalEvidenceHash()); entity.setBusinessScope(value.businessScope());
        entity.setEffectiveAt(local(value.effectiveAt())); entity.setRequestedBy(value.requestedBy());
        entity.setStatus(value.status().name()); entity.setVersion(value.version());
        return entity;
    }

    static PersistentActivationRequest toDomain(RoleRuntimeActivationRequestEntity value) {
        return new PersistentActivationRequest(value.getId(), value.getActivationId(), value.getResolverCode(),
                value.getResolverVersion(), value.getContractHash(), value.getBindingHash(), value.getCandidateHash(),
                value.getDirectoryContractHash(), value.getDirectoryRevision(), value.getActivationHash(),
                value.getApprovalEvidenceHash(), value.getBusinessScope(), instant(value.getEffectiveAt()),
                value.getRequestedBy(), PersistentActivationStatus.valueOf(value.getStatus()), value.getVersion());
    }

    static RoleRuntimeActivationApprovalEntity toEntity(PersistentActivationDecision value) {
        var entity = new RoleRuntimeActivationApprovalEntity();
        entity.setId(value.id()); entity.setActivationId(value.activationId()); entity.setApproverType(value.approverType());
        entity.setApproverId(value.approverId()); entity.setDecision(value.decision()); entity.setReason(value.reason());
        entity.setSourceEvidenceHash(value.sourceEvidenceHash()); entity.setDecisionHash(value.decisionHash());
        entity.setActivationHash(value.activationHash()); entity.setContractHash(value.contractHash());
        entity.setBindingHash(value.bindingHash());
        entity.setDecisionTime(local(value.decisionTime())); entity.setVersion(value.version());
        return entity;
    }

    static PersistentActivationDecision toDomain(RoleRuntimeActivationApprovalEntity value) {
        return new PersistentActivationDecision(value.getId(), value.getActivationId(), value.getApproverType(),
                value.getApproverId(), value.getDecision(), value.getReason(), value.getSourceEvidenceHash(),
                value.getDecisionHash(), value.getActivationHash(), value.getContractHash(),
                value.getBindingHash(), instant(value.getDecisionTime()), value.getVersion());
    }

    static RoleRuntimeActivationEvidenceEntity toEntity(ActivationEvidenceRecord value) {
        var entity = new RoleRuntimeActivationEvidenceEntity();
        entity.setId(value.id()); entity.setApprovalId(value.approvalId()); entity.setActivationId(value.activationId());
        entity.setEvidenceType(value.evidenceType().name()); entity.setEvidenceHash(value.evidenceHash());
        entity.setActivationHash(value.activationHash()); entity.setContractHash(value.contractHash());
        entity.setBindingHash(value.bindingHash());
        entity.setCanonicalVersion(value.canonicalVersion()); entity.setCreatedTime(local(value.createdTime()));
        return entity;
    }

    static ActivationEvidenceRecord toDomain(RoleRuntimeActivationEvidenceEntity value) {
        return new ActivationEvidenceRecord(value.getId(), value.getApprovalId(), value.getActivationId(),
                ActivationEvidenceType.valueOf(value.getEvidenceType()), value.getEvidenceHash(),
                value.getActivationHash(), value.getContractHash(), value.getBindingHash(),
                value.getCanonicalVersion(), instant(value.getCreatedTime()));
    }

    private static LocalDateTime local(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC); }
}
