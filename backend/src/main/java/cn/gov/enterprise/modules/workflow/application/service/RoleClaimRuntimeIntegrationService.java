package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.ClaimWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.application.command.RoleClaimRuntimeRequest;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidateMemberStatus;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaimResult;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.*;
import cn.gov.enterprise.security.SecurityPrincipal;
import org.springframework.stereotype.Service;

/** Internal two-phase ROLE Claim orchestrator. No Controller exposes this while runtime is disabled. */
@Service
public final class RoleClaimRuntimeIntegrationService {
    private final WorkflowTaskRepository tasks;
    private final CandidatePoolRepository pools;
    private final RoleClaimEvidenceAssembler assembler;
    private final RoleClaimRuntimeTransactionService transaction;
    private final RoleClaimNotCommittedEvidenceService notCommitted;

    public RoleClaimRuntimeIntegrationService(WorkflowTaskRepository tasks, CandidatePoolRepository pools,
            RoleClaimEvidenceAssembler assembler, RoleClaimRuntimeTransactionService transaction,
            RoleClaimNotCommittedEvidenceService notCommitted) {
        this.tasks=tasks; this.pools=pools; this.assembler=assembler; this.transaction=transaction;
        this.notCommitted=notCommitted;
    }

    public TaskClaimResult prepareVerifyAndClaim(Long taskId, ClaimWorkflowTaskCommand command,
            RealtimeEligibilityContext context, SecurityPrincipal principal, String traceId) {
        var task=tasks.findById(taskId).orElseThrow(()->new BusinessException("B2674","workflow task does not exist"));
        var pool=pools.findByTaskId(taskId).orElseThrow(()->new BusinessException("B2674","Candidate Pool does not exist"));
        var member=pool.members().stream().filter(m->m.candidateUserId().equals(principal.userId())
                && m.status()==CandidateMemberStatus.INCLUDED).findFirst()
                .orElseThrow(()->new BusinessException("B2673","current user is not a frozen Candidate"));
        if (context.query().taskId()!=taskId || context.query().candidatePoolId()!=pool.id()
                || context.query().candidateUserId()!=principal.userId()) {
            throw new BusinessException("B2678","ROLE Prepare ownership mismatch");
        }
        var preparation=new RoleRealtimeEligibilityPreparationService(new RealtimeEligibilityValidator()).prepare(context);
        var token=preparation.verificationToken().orElseThrow(()->new BusinessException("B2675",
                preparation.result().decision().failure().code().name()));
        var bundle=assembler.assemble(preparation.result(),context,pool,member,
                context.query().correlationId(),command.idempotencyKey());
        try {
            return transaction.claim(taskId,new RoleClaimRuntimeRequest(command,token,bundle),principal,traceId);
        } catch (BusinessException failure) {
            notCommitted.record(bundle,failure.getCode(),principal.userId().toString());
            throw failure;
        }
    }
}
