package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.ClaimWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.domain.candidate.*;
import cn.gov.enterprise.modules.workflow.domain.claim.*;
import cn.gov.enterprise.modules.workflow.domain.model.*;
import cn.gov.enterprise.modules.workflow.domain.repository.*;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable USER/DIRECT-compatible Candidate Pool Claim transaction. ROLE has an isolated service. */
@Service
public class TaskClaimTransactionService {
    private final WorkflowTaskRepository tasks; private final CandidatePoolRepository pools;
    private final CandidatePoolMemberRepository members; private final WorkflowNodeExecutionRepository executions;
    private final WorkflowInstanceRepository instances; private final TaskClaimRepository claims;
    private final TaskClaimAuditRepository audits; private final RealtimeEligibilityChecker eligibilityChecker;
    private final SegregationOfDutiesPolicy sodPolicy; private final WorkflowIdentityGenerator ids; private final Clock clock;

    @Autowired
    public TaskClaimTransactionService(WorkflowTaskRepository tasks,CandidatePoolRepository pools,
            CandidatePoolMemberRepository members,WorkflowNodeExecutionRepository executions,
            WorkflowInstanceRepository instances,TaskClaimRepository claims,TaskClaimAuditRepository audits,
            RealtimeEligibilityChecker eligibilityChecker,SegregationOfDutiesPolicy sodPolicy,WorkflowIdentityGenerator ids){
        this(tasks,pools,members,executions,instances,claims,audits,eligibilityChecker,sodPolicy,ids,Clock.systemDefaultZone());}
    public TaskClaimTransactionService(WorkflowTaskRepository tasks,CandidatePoolRepository pools,
            CandidatePoolMemberRepository members,WorkflowNodeExecutionRepository executions,
            WorkflowInstanceRepository instances,TaskClaimRepository claims,TaskClaimAuditRepository audits,
            RealtimeEligibilityChecker eligibilityChecker,SegregationOfDutiesPolicy sodPolicy,
            WorkflowIdentityGenerator ids,Clock clock){this.tasks=tasks;this.pools=pools;this.members=members;
        this.executions=executions;this.instances=instances;this.claims=claims;this.audits=audits;
        this.eligibilityChecker=eligibilityChecker;this.sodPolicy=sodPolicy;this.ids=ids;this.clock=clock;}

    @Transactional
    public TaskClaimResult claim(Long taskId,ClaimWorkflowTaskCommand command,SecurityPrincipal principal,String traceId){
        positive(taskId,"taskId");String key=text(command.idempotencyKey(),"idempotencyKey",128);LocalDateTime now=LocalDateTime.now(clock);
        WorkflowTask task=tasks.findByIdForUpdate(taskId).orElseThrow(()->error("B2674","workflow task does not exist"));
        TaskClaim replay=claims.findByTaskIdAndIdempotencyKey(taskId,key).orElse(null);
        if(replay!=null){if(!replay.candidateUserId().equals(principal.userId()))throw error("B2679","idempotency key belongs to another claimant");return TaskClaimResult.from(replay,true);}
        if(task.assignmentMode()==WorkflowTask.AssignmentMode.DIRECT)throw error("B2671","DIRECT_ASSIGNMENT_NOT_CLAIMABLE");
        if(task.status()!=WorkflowTask.Status.PENDING)throw error("B2672","workflow task is not claimable");
        CandidatePool pool=pools.findByTaskIdForUpdate(taskId).orElseThrow(()->error("B2674","Candidate Pool does not exist"));
        var member=members.findByPoolIdAndUserIdForUpdate(pool.id(),principal.userId()).orElseThrow(()->error("B2673","current user is not a frozen Candidate"));
        if(member.status()!=CandidateMemberStatus.INCLUDED)throw error("B2673","frozen Candidate is not eligible");
        WorkflowNodeExecution execution=executions.findByIdForUpdate(task.nodeExecutionId()).orElseThrow(()->error("B2674","node execution does not exist"));
        WorkflowInstance instance=instances.findByIdForUpdate(task.instanceId()).orElseThrow(()->error("B2674","workflow instance does not exist"));
        ownership(task,pool,execution,instance);
        if(execution.status()!=WorkflowNodeExecution.Status.ACTIVE)throw error("B2672","node execution is not ACTIVE");
        if(instance.status()!=WorkflowInstance.Status.RUNNING)throw error("B2672","workflow instance is not RUNNING");
        CandidatePool claimedPool;WorkflowTask claimedTask;try{claimedPool=pool.claimed(now);claimedTask=task.claim(principal.userId(),now);}
        catch(IllegalStateException ex){throw error("B2672",ex.getMessage());}
        var context=new TaskClaimContext(task.id(),instance.id(),execution.id(),principal.userId(),principal.orgId(),
                instance.initiatorUserId(),instance.initiatorOrgId(),instance.businessType(),instance.businessKey());
        var realtime=eligibilityChecker.check(context);if(!realtime.allowed())throw error("B2675",realtime.reasonCode());
        String scope=dataScope(instance,principal);var sod=sodPolicy.evaluate(context);if(!sod.allowed())throw error("B2676",sod.reasonCode());
        long claimId=ids.nextId();TaskClaim claim=TaskClaim.claimed(claimId,task.id(),pool.id(),member.id(),instance.id(),execution.id(),
                principal.userId(),now,key,traceId,member.eligibilityHash(),realtime.evidenceSummary(),scope,
                sod.policyCode()+":"+sod.policyVersion()+":"+sod.reasonCode(),task.version());
        TaskClaim persisted=claims.insert(claim);if(!tasks.claim(claimedTask,task.version()))throw error("B2679","workflow task was claimed concurrently");
        if(!pools.claim(claimedPool,pool.version()))throw error("B2679","Candidate Pool was claimed concurrently");
        audits.appendSuccess(ids.nextId(),persisted);return TaskClaimResult.from(claim,false);
    }
    private String dataScope(WorkflowInstance i,SecurityPrincipal p){boolean allowed=p.allDataScope()||i.initiatorUserId().equals(p.userId())
                ||(p.allowedOrgIds()!=null&&p.allowedOrgIds().contains(i.initiatorOrgId()));if(!allowed)throw error("B2677","workflow task is outside current data scope");return p.allDataScope()?"ALLOW:ALL":"ALLOW:SCOPED";}
    private void ownership(WorkflowTask t,CandidatePool p,WorkflowNodeExecution e,WorkflowInstance i){if(!t.id().equals(p.taskId())
                ||!t.instanceId().equals(p.instanceId())||!t.nodeExecutionId().equals(p.nodeExecutionId())||!t.instanceId().equals(e.instanceId())
                ||!t.nodeId().equals(e.nodeId())||!t.instanceId().equals(i.id())||!t.versionId().equals(i.versionId()))throw error("B2678","Claim ownership chain is inconsistent");}
    private static BusinessException error(String c,String m){return new BusinessException(c,m);}private static void positive(Long v,String f){if(v==null||v<=0)throw error("B2670",f+" must be positive");}
    private static String text(String v,String f,int max){if(v==null||v.isBlank()||v.trim().length()>max)throw error("B2670",f+" is invalid");return v.trim();}
}
