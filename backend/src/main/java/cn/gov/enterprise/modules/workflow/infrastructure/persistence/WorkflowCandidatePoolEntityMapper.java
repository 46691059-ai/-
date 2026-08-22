package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidateMemberStatus;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolHash;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolMember;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolStatus;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowCandidatePoolEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowCandidatePoolMemberEntity;
import java.util.List;

final class WorkflowCandidatePoolEntityMapper {
    private WorkflowCandidatePoolEntityMapper() { }

    static WorkflowCandidatePoolEntity toEntity(CandidatePool value) {
        WorkflowCandidatePoolEntity entity = new WorkflowCandidatePoolEntity();
        entity.setId(value.id()); entity.setPoolNo(value.poolNo());
        entity.setTaskId(value.taskId()); entity.setInstanceId(value.instanceId());
        entity.setVersionId(value.versionId()); entity.setNodeId(value.nodeId());
        entity.setNodeExecutionId(value.nodeExecutionId()); entity.setBindingSetId(value.bindingSetId());
        entity.setResolverBindingId(value.resolverBindingId());
        entity.setNodeResolverBindingId(value.nodeResolverBindingId());
        entity.setAssignmentSnapshotId(value.assignmentSnapshotId());
        entity.setAssignmentMode(value.assignmentMode().name());
        entity.setStrategyType(value.strategyType().name());
        entity.setResolverCode(value.resolverCode()); entity.setResolverVersion(value.resolverVersion());
        entity.setContractHash(value.contractHash().value()); entity.setRuleHash(value.ruleHash());
        entity.setCandidateCount(value.candidateCount()); entity.setGeneratedTime(value.generatedTime());
        entity.setEffectiveTime(value.effectiveTime()); entity.setExpiresTime(value.expiresTime());
        entity.setPoolHash(value.poolHash().value()); entity.setStatus(value.status().name());
        entity.setAuditInfo(value.auditInfo()); entity.setVersion(value.version());
        return entity;
    }

    static CandidatePool toDomain(
            WorkflowCandidatePoolEntity entity, List<CandidatePoolMember> members) {
        if (!entity.getCandidateCount().equals(members.size())) {
            throw new IllegalStateException("Candidate Pool member count is inconsistent");
        }
        return new CandidatePool(entity.getId(), entity.getPoolNo(), entity.getTaskId(),
                entity.getInstanceId(), entity.getVersionId(), entity.getNodeId(),
                entity.getNodeExecutionId(), entity.getBindingSetId(), entity.getResolverBindingId(),
                entity.getNodeResolverBindingId(), entity.getAssignmentSnapshotId(),
                ResolverMode.valueOf(entity.getAssignmentMode()),
                AssignmentStrategy.Type.valueOf(entity.getStrategyType()), entity.getResolverCode(),
                entity.getResolverVersion(), ResolverContractHash.of(entity.getContractHash()),
                entity.getRuleHash(), entity.getGeneratedTime(), entity.getEffectiveTime(),
                entity.getExpiresTime(), new CandidatePoolHash(entity.getPoolHash()),
                CandidatePoolStatus.valueOf(entity.getStatus()), members,
                entity.getAuditInfo(), entity.getVersion());
    }

    static WorkflowCandidatePoolMemberEntity toEntity(CandidatePoolMember value) {
        WorkflowCandidatePoolMemberEntity entity = new WorkflowCandidatePoolMemberEntity();
        entity.setId(value.id()); entity.setPoolId(value.poolId());
        entity.setTaskId(value.taskId()); entity.setInstanceId(value.instanceId());
        entity.setCandidateUserId(value.candidateUserId());
        entity.setSourceType(value.sourceType().name());
        entity.setSourceRefSnapshot(value.sourceRefSnapshot());
        entity.setOrgIdSnapshot(value.orgIdSnapshot());
        entity.setPositionIdSnapshot(value.positionIdSnapshot());
        entity.setRoleIdSnapshot(value.roleIdSnapshot());
        entity.setEligibilitySnapshot(value.eligibilitySnapshot());
        entity.setEligibilityHash(value.eligibilityHash());
        entity.setSortOrder(value.sortOrder()); entity.setGeneratedTime(value.generatedTime());
        entity.setStatus(value.status().name()); entity.setAuditInfo(value.auditInfo());
        entity.setVersion(value.version());
        return entity;
    }

    static CandidatePoolMember toDomain(WorkflowCandidatePoolMemberEntity entity) {
        return new CandidatePoolMember(entity.getId(), entity.getPoolId(), entity.getTaskId(),
                entity.getInstanceId(), entity.getCandidateUserId(),
                AssignmentStrategy.Type.valueOf(entity.getSourceType()), entity.getSourceRefSnapshot(),
                entity.getOrgIdSnapshot(), entity.getPositionIdSnapshot(), entity.getRoleIdSnapshot(),
                entity.getEligibilitySnapshot(), entity.getEligibilityHash(), entity.getSortOrder(),
                entity.getGeneratedTime(), CandidateMemberStatus.valueOf(entity.getStatus()),
                entity.getAuditInfo(), entity.getVersion());
    }
}
