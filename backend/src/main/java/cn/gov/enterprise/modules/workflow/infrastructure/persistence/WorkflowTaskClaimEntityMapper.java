package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaim;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaimStatus;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskClaimEntity;

final class WorkflowTaskClaimEntityMapper {
    private WorkflowTaskClaimEntityMapper() {
    }

    static WorkflowTaskClaimEntity toEntity(TaskClaim value) {
        WorkflowTaskClaimEntity e = new WorkflowTaskClaimEntity();
        e.setId(value.id()); e.setClaimNo(value.claimNo()); e.setTaskId(value.taskId());
        e.setCandidatePoolId(value.candidatePoolId()); e.setCandidateMemberId(value.candidateMemberId());
        e.setInstanceId(value.instanceId()); e.setNodeExecutionId(value.nodeExecutionId());
        e.setCandidateUserId(value.candidateUserId()); e.setOperatorUserId(value.operatorUserId());
        e.setStatus(value.status().name()); e.setClaimTime(value.claimTime());
        e.setEligibilitySnapshotHash(value.eligibilitySnapshotHash());
        e.setEligibilityEvidenceId(value.eligibilityEvidenceId());
        e.setEligibilityContractVersion(value.eligibilityContractVersion());
        e.setAdmissionId(value.admissionId());
        e.setRealtimeEligibilityResult(value.realtimeEligibilityResult());
        e.setDataScopeResult(value.dataScopeResult()); e.setSodResult(value.sodResult());
        e.setRbacResult(value.rbacResult()); e.setTaskStatusBefore(value.taskStatusBefore());
        e.setTaskStatusAfter(value.taskStatusAfter()); e.setPoolStatusBefore(value.poolStatusBefore());
        e.setPoolStatusAfter(value.poolStatusAfter()); e.setTaskVersionBefore(value.taskVersionBefore());
        e.setTaskVersionAfter(value.taskVersionAfter()); e.setIdempotencyKey(value.idempotencyKey());
        e.setTraceId(value.traceId()); e.setActiveToken(value.activeToken()); e.setVersion(value.version());
        return e;
    }

    static TaskClaim toDomain(WorkflowTaskClaimEntity e) {
        try {
            return new TaskClaim(e.getId(), e.getClaimNo(), e.getTaskId(), e.getCandidatePoolId(),
                    e.getCandidateMemberId(), e.getInstanceId(), e.getNodeExecutionId(),
                    e.getCandidateUserId(), e.getOperatorUserId(), e.getClaimTime(),
                    e.getIdempotencyKey(), e.getTraceId(), TaskClaimStatus.valueOf(e.getStatus()),
                    e.getEligibilitySnapshotHash(), e.getRealtimeEligibilityResult(),
                    e.getDataScopeResult(), e.getSodResult(), e.getRbacResult(),
                    e.getTaskStatusBefore(), e.getTaskStatusAfter(), e.getPoolStatusBefore(),
                    e.getPoolStatusAfter(), e.getTaskVersionBefore(), e.getTaskVersionAfter(),
                    e.getActiveToken(), e.getVersion(), e.getEligibilityEvidenceId(),
                    e.getEligibilityContractVersion(), null, e.getAdmissionId());
        } catch (RuntimeException exception) {
            BusinessException corrupt = new BusinessException("B2678", "workflow claim data is corrupt");
            corrupt.initCause(exception);
            throw corrupt;
        }
    }
}
