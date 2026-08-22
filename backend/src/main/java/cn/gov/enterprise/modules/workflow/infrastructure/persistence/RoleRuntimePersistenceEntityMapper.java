package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeApproval;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeBindingSnapshot;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeEvidence;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.RoleRuntimeBindingApprovalEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRuntimeBindingSnapshotEntity;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class RoleRuntimePersistenceEntityMapper {
    private RoleRuntimePersistenceEntityMapper() { }

    static RoleRuntimeBindingApprovalEntity toEntity(RoleRuntimeApproval value) {
        RoleRuntimeBindingApprovalEntity entity = new RoleRuntimeBindingApprovalEntity();
        entity.setId(value.id());
        entity.setProposalHash(value.proposalHash());
        entity.setEligibilityHash(value.eligibilityHash());
        entity.setResolverCode(value.resolverCode().value());
        entity.setResolverVersion(value.resolverVersion().value());
        entity.setContractHash(value.contractHash().value());
        entity.setStatus(value.status().name());
        entity.setApprovedBy(value.approvedBy());
        entity.setApprovedAt(local(value.approvedAt()));
        entity.setRejectReason(value.rejectReason());
        entity.setAuditInfo(value.auditInfo());
        entity.setVersion(value.version());
        return entity;
    }

    static RoleRuntimeApproval toDomain(RoleRuntimeBindingApprovalEntity entity) {
        return new RoleRuntimeApproval(entity.getId(), entity.getProposalHash(),
                entity.getEligibilityHash(), ResolverCode.of(entity.getResolverCode()),
                ResolverVersion.of(entity.getResolverVersion()),
                ResolverContractHash.of(entity.getContractHash()),
                RoleRuntimeApproval.Status.valueOf(entity.getStatus()),
                entity.getApprovedBy(), instant(entity.getApprovedAt()),
                entity.getRejectReason(), entity.getAuditInfo(), entity.getVersion());
    }

    static WorkflowRoleRuntimeBindingSnapshotEntity toEntity(RoleRuntimeBindingSnapshot value) {
        WorkflowRoleRuntimeBindingSnapshotEntity entity =
                new WorkflowRoleRuntimeBindingSnapshotEntity();
        entity.setId(value.id());
        entity.setApprovalId(value.approvalId());
        entity.setBindingSetId(value.bindingSetId());
        entity.setResolverBindingId(value.resolverBindingId());
        entity.setNodeResolverBindingId(value.nodeResolverBindingId());
        entity.setInstanceId(value.instanceId());
        entity.setDefinitionVersionId(value.definitionVersionId());
        entity.setNodeId(value.nodeId());
        entity.setResolverCode(value.resolverCode().value());
        entity.setResolverVersion(value.resolverVersion().value());
        entity.setContractHash(value.contractHash().value());
        entity.setRoleCode(value.roleCode());
        entity.setOrganizationId(value.organizationId());
        entity.setDirectoryRevision(value.evidence().directoryRevision());
        entity.setEffectiveAt(local(value.effectiveAt()));
        entity.setDirectoryHash(value.evidence().directoryHash());
        entity.setRoleRuleHash(value.evidence().roleRuleHash());
        entity.setCandidateRuleHash(value.evidence().candidateRuleHash());
        entity.setSourceEvidenceHash(value.evidence().sourceEvidenceHash());
        entity.setBindingHash(value.bindingHash());
        entity.setStatus(value.status().name());
        entity.setAuditInfo(value.auditInfo());
        entity.setVersion(value.version());
        return entity;
    }

    static RoleRuntimeBindingSnapshot toDomain(WorkflowRoleRuntimeBindingSnapshotEntity entity) {
        return new RoleRuntimeBindingSnapshot(entity.getId(), entity.getApprovalId(),
                entity.getBindingSetId(), entity.getResolverBindingId(),
                entity.getNodeResolverBindingId(), entity.getInstanceId(),
                entity.getDefinitionVersionId(), entity.getNodeId(),
                ResolverCode.of(entity.getResolverCode()),
                ResolverVersion.of(entity.getResolverVersion()),
                ResolverContractHash.of(entity.getContractHash()), entity.getRoleCode(),
                entity.getOrganizationId(), new RoleRuntimeEvidence(
                        entity.getDirectoryRevision(), entity.getDirectoryHash(),
                        entity.getRoleRuleHash(), entity.getCandidateRuleHash(),
                        entity.getSourceEvidenceHash()), instant(entity.getEffectiveAt()),
                entity.getBindingHash(),
                RoleRuntimeBindingSnapshot.Status.valueOf(entity.getStatus()),
                entity.getAuditInfo(), entity.getVersion());
    }

    private static LocalDateTime local(java.time.Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static java.time.Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
