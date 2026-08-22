package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingCandidateSnapshot;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingLifecycleEvent;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingLifecycleStatus;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingPromotion;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingPromotionReference;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleBindingCandidateSnapshotEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleBindingPromotionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleBindingSnapshotEventEntity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class RoleRuntimeBindingFoundationEntityMapper {
    private RoleRuntimeBindingFoundationEntityMapper() { }

    static WorkflowRoleBindingPromotionEntity toEntity(RoleRuntimeBindingPromotion value) {
        var entity = new WorkflowRoleBindingPromotionEntity();
        entity.setId(value.id()); entity.setPromotionId(value.promotionId());
        entity.setActivationId(value.activationId()); entity.setActivationDeleteToken(0L);
        entity.setActivationHash(value.activationHash()); entity.setActivationAuditHash(value.activationAuditHash());
        entity.setApprovalEvidenceHash(value.approvalEvidenceHash()); entity.setResolverCode(value.resolverCode().value());
        entity.setResolverVersion(value.resolverVersion().value()); entity.setResolverContractHash(value.resolverContractHash().value());
        entity.setBindingHash(value.bindingHash()); entity.setCandidateHash(value.candidateHash());
        entity.setDirectoryContractHash(value.directoryContractHash()); entity.setDirectoryRevision(value.directoryRevision());
        entity.setBusinessScope(value.businessScope()); entity.setEffectiveFrom(local(value.effectiveFrom()));
        entity.setEffectiveUntil(local(value.effectiveUntil())); entity.setPermissionEvidenceHash(value.permissionEvidenceHash());
        entity.setPromotionEvidenceHash(value.promotionEvidenceHash()); entity.setPromotionHash(value.promotionHash());
        entity.setStatus("PROMOTED"); entity.setPromotedAt(local(value.promotedAt()));
        entity.setPromotedBy(value.promotedBy()); entity.setCanonicalVersion("ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1");
        entity.setVersion(value.version());
        return entity;
    }

    static RoleRuntimeBindingPromotion toDomain(WorkflowRoleBindingPromotionEntity value) {
        return new RoleRuntimeBindingPromotion(value.getId(), value.getPromotionId(), value.getActivationId(),
                value.getActivationHash(), value.getActivationAuditHash(), value.getApprovalEvidenceHash(),
                ResolverCode.of(value.getResolverCode()), ResolverVersion.of(value.getResolverVersion()),
                ResolverContractHash.of(value.getResolverContractHash()), value.getBindingHash(),
                value.getCandidateHash(), value.getDirectoryContractHash(), value.getDirectoryRevision(),
                value.getBusinessScope(), instant(value.getEffectiveFrom()), instant(value.getEffectiveUntil()),
                value.getPermissionEvidenceHash(), value.getPromotionEvidenceHash(), value.getPromotionHash(),
                instant(value.getPromotedAt()), value.getPromotedBy(), value.getVersion());
    }

    static WorkflowRoleBindingCandidateSnapshotEntity toEntity(
            RoleRuntimeBindingCandidateSnapshot value) {
        var entity = new WorkflowRoleBindingCandidateSnapshotEntity();
        var reference = value.promotionReference(); var evidence = value.evidence();
        entity.setId(value.id()); entity.setSnapshotId(value.snapshotId());
        entity.setPromotionRowId(reference.promotionRowId()); entity.setPromotionId(reference.promotionId());
        entity.setActivationId(reference.activationId()); entity.setActivationDeleteToken(0L);
        entity.setActivationHash(reference.activationHash()); entity.setPromotionHash(reference.promotionHash());
        entity.setPromotionEvidenceHash(reference.promotionEvidenceHash());
        entity.setPromotionReferenceHash(reference.referenceHash()); entity.setResolverCode(value.resolverCode().value());
        entity.setResolverVersion(value.resolverVersion().value()); entity.setResolverContractHash(value.resolverContractHash().value());
        entity.setBindingHash(value.bindingHash()); entity.setCandidateHash(value.candidateHash());
        entity.setDirectoryContractHash(evidence.directoryContractHash()); entity.setDirectoryRevision(value.directoryRevision());
        entity.setBusinessScope(value.businessScope()); entity.setEffectiveFrom(local(value.effectiveFrom()));
        entity.setEffectiveUntil(local(value.effectiveUntil())); entity.setActivationAuditHash(evidence.activationAuditHash());
        entity.setApprovalEvidenceHash(evidence.approvalEvidenceHash()); entity.setPermissionEvidenceHash(evidence.permissionEvidenceHash());
        entity.setEvidenceSetHash(evidence.evidenceSetHash()); entity.setSnapshotHash(value.snapshotHash());
        entity.setCanonicalVersion(value.canonicalVersion()); entity.setCreatedTime(local(value.createdAt()));
        entity.setCreatedBy(value.createdBy()); entity.setVersion(value.version());
        return entity;
    }

    static RoleRuntimeBindingCandidateSnapshot toDomain(
            WorkflowRoleBindingCandidateSnapshotEntity value) {
        var reference = new RoleRuntimeBindingPromotionReference(value.getPromotionRowId(),
                value.getPromotionId(), value.getActivationId(), value.getActivationHash(),
                value.getPromotionHash(), value.getPromotionEvidenceHash(), value.getPromotionReferenceHash());
        var evidence = new RoleRuntimeBindingEvidence(value.getActivationAuditHash(),
                value.getApprovalEvidenceHash(), value.getPromotionEvidenceHash(),
                value.getPermissionEvidenceHash(), value.getDirectoryContractHash(),
                value.getEvidenceSetHash());
        return new RoleRuntimeBindingCandidateSnapshot(value.getId(), value.getSnapshotId(), reference,
                ResolverCode.of(value.getResolverCode()), ResolverVersion.of(value.getResolverVersion()),
                ResolverContractHash.of(value.getResolverContractHash()), value.getBindingHash(),
                value.getCandidateHash(), value.getDirectoryRevision(), value.getBusinessScope(),
                instant(value.getEffectiveFrom()), instant(value.getEffectiveUntil()), evidence,
                value.getSnapshotHash(), value.getCanonicalVersion(), instant(value.getCreatedTime()),
                value.getCreatedBy(), value.getVersion());
    }

    static WorkflowRoleBindingSnapshotEventEntity toEntity(RoleRuntimeBindingLifecycleEvent value) {
        var entity = new WorkflowRoleBindingSnapshotEventEntity();
        entity.setId(value.id()); entity.setSnapshotRowId(value.snapshotRowId());
        entity.setSnapshotId(value.snapshotId()); entity.setPromotionId(value.promotionId());
        entity.setActivationId(value.activationId()); entity.setSequenceNo(value.sequenceNo());
        entity.setFromStatus(value.fromStatus() == null ? null : value.fromStatus().name());
        entity.setToStatus(value.toStatus().name()); entity.setReasonCode(value.reasonCode());
        entity.setSourceEvidenceHash(value.sourceEvidenceHash()); entity.setPreviousEventHash(value.previousEventHash());
        entity.setEventHash(value.eventHash()); entity.setOccurredAt(local(value.occurredAt()));
        entity.setOperatorId(value.operatorId()); entity.setCanonicalVersion(value.canonicalVersion());
        entity.setVersion(value.version());
        return entity;
    }

    static RoleRuntimeBindingLifecycleEvent toDomain(WorkflowRoleBindingSnapshotEventEntity value) {
        return new RoleRuntimeBindingLifecycleEvent(value.getId(), value.getSnapshotRowId(),
                value.getSnapshotId(), value.getPromotionId(), value.getActivationId(), value.getSequenceNo(),
                value.getFromStatus() == null ? null : RoleRuntimeBindingLifecycleStatus.valueOf(value.getFromStatus()),
                RoleRuntimeBindingLifecycleStatus.valueOf(value.getToStatus()), value.getReasonCode(),
                value.getSourceEvidenceHash(), value.getPreviousEventHash(), value.getEventHash(),
                instant(value.getOccurredAt()), value.getOperatorId(), value.getCanonicalVersion(), value.getVersion());
    }

    private static LocalDateTime local(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
