package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaim;
import cn.gov.enterprise.modules.workflow.domain.repository.TaskClaimAuditRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskClaimAuditEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskClaimAuditMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.eligibility.ExternalAuditOutboxWriter;
import org.springframework.stereotype.Repository;

@Repository
public class TaskClaimAuditRepositoryImpl implements TaskClaimAuditRepository {
    private final WorkflowTaskClaimAuditMapper mapper;
    private final WorkflowPersistenceAudit audit;
    private final ExternalAuditOutboxWriter externalAudit;

    public TaskClaimAuditRepositoryImpl(
            WorkflowTaskClaimAuditMapper mapper, WorkflowPersistenceAudit audit,
            ExternalAuditOutboxWriter externalAudit) {
        this.mapper = mapper; this.audit = audit; this.externalAudit = externalAudit;
    }

    @Override
    public void appendSuccess(Long auditId, TaskClaim claim) {
        WorkflowTaskClaimAuditEntity e = new WorkflowTaskClaimAuditEntity();
        e.setId(auditId); e.setEventNo("WCAE-" + auditId); e.setTaskId(claim.taskId());
        e.setCandidatePoolId(claim.candidatePoolId()); e.setClaimId(claim.id());
        e.setCandidateMemberId(claim.candidateMemberId()); e.setInstanceId(claim.instanceId());
        e.setNodeExecutionId(claim.nodeExecutionId()); e.setOperatorUserId(claim.operatorUserId());
        e.setClaimantUserId(claim.candidateUserId()); e.setEventType("CLAIM");
        e.setResult("SUCCESS"); e.setReasonCode("CLAIMED");
        e.setFrozenEligibilityHash(claim.eligibilitySnapshotHash());
        e.setEligibilityEvidenceId(claim.eligibilityEvidenceId());
        e.setEligibilityPersistenceHash(claim.eligibilityPersistenceHash());
        e.setRealtimeEligibilityResult(claim.realtimeEligibilityResult());
        e.setRbacResult(claim.rbacResult()); e.setDataScopeResult(claim.dataScopeResult());
        e.setSodResult(claim.sodResult()); e.setTaskStatusBefore(claim.taskStatusBefore());
        e.setTaskStatusAfter(claim.taskStatusAfter()); e.setPoolStatusBefore(claim.poolStatusBefore());
        e.setPoolStatusAfter(claim.poolStatusAfter()); e.setEventTime(claim.claimTime());
        e.setTraceId(claim.traceId()); e.setIdempotencyKey(claim.idempotencyKey());
        e.setPreviousEventHash(null);
        e.setEventHash(ResolverContractHash.sha256(String.join("|", claim.claimNo(),
                claim.taskId().toString(), claim.candidatePoolId().toString(),
                claim.candidateUserId().toString(), claim.idempotencyKey(),
                claim.eligibilitySnapshotHash(), claim.realtimeEligibilityResult(),
                claim.dataScopeResult(), claim.sodResult())).value());
        audit.initialize(e);
        if (mapper.insert(e) != 1) throw new BusinessException("B2670", "Claim audit save failed");
        externalAudit.enqueue(auditId, e.getEventHash(), claim);
    }
}
