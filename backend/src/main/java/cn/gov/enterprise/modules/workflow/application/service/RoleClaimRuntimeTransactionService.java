package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.RoleClaimRuntimeRequest;
import cn.gov.enterprise.modules.workflow.domain.candidate.*;
import cn.gov.enterprise.modules.workflow.domain.claim.*;
import cn.gov.enterprise.modules.workflow.domain.model.*;
import cn.gov.enterprise.modules.workflow.domain.repository.*;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.*;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityVerificationToken;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dedicated ROLE_CLAIM short transaction; USER/DIRECT never depends on this service. */
@Service
public class RoleClaimRuntimeTransactionService {
    private final WorkflowTaskRepository tasks; private final CandidatePoolRepository pools;
    private final CandidatePoolMemberRepository members; private final WorkflowNodeExecutionRepository executions;
    private final WorkflowInstanceRepository instances; private final TaskClaimRepository claims;
    private final TaskClaimAuditRepository audits; private final SegregationOfDutiesPolicy sodPolicy;
    private final WorkflowIdentityGenerator ids; private final Clock clock;
    private final RoleClaimRuntimeGate runtimeGate; private final RoleClaimAdmissionGate admissionGate;
    private final RoleClaimCommitCapabilityGate commitCapabilityGate;
    private final RoleRealtimeEligibilityEvidenceRepository evidence;

    @Autowired
    public RoleClaimRuntimeTransactionService(WorkflowTaskRepository tasks,CandidatePoolRepository pools,
            CandidatePoolMemberRepository members,WorkflowNodeExecutionRepository executions,
            WorkflowInstanceRepository instances,TaskClaimRepository claims,TaskClaimAuditRepository audits,
            SegregationOfDutiesPolicy sodPolicy,WorkflowIdentityGenerator ids,RoleClaimRuntimeGate runtimeGate,
            RoleClaimAdmissionGate admissionGate,RoleRealtimeEligibilityEvidenceRepository evidence,
            RoleClaimCommitCapabilityGate commitCapabilityGate){
        this(tasks,pools,members,executions,instances,claims,audits,sodPolicy,ids,Clock.systemUTC(),
                runtimeGate,admissionGate,evidence,commitCapabilityGate);
    }
    RoleClaimRuntimeTransactionService(WorkflowTaskRepository tasks,CandidatePoolRepository pools,
            CandidatePoolMemberRepository members,WorkflowNodeExecutionRepository executions,
            WorkflowInstanceRepository instances,TaskClaimRepository claims,TaskClaimAuditRepository audits,
            SegregationOfDutiesPolicy sodPolicy,WorkflowIdentityGenerator ids,Clock clock,
            RoleClaimRuntimeGate runtimeGate,RoleClaimAdmissionGate admissionGate,
            RoleRealtimeEligibilityEvidenceRepository evidence){
        this(tasks,pools,members,executions,instances,claims,audits,sodPolicy,ids,clock,runtimeGate,admissionGate,evidence,
                facts->new RoleClaimCommitCapabilityGate.Decision(true,"TEST_COMPATIBILITY","0".repeat(64)));
    }
    RoleClaimRuntimeTransactionService(WorkflowTaskRepository tasks,CandidatePoolRepository pools,
            CandidatePoolMemberRepository members,WorkflowNodeExecutionRepository executions,
            WorkflowInstanceRepository instances,TaskClaimRepository claims,TaskClaimAuditRepository audits,
            SegregationOfDutiesPolicy sodPolicy,WorkflowIdentityGenerator ids,Clock clock,
            RoleClaimRuntimeGate runtimeGate,RoleClaimAdmissionGate admissionGate,
            RoleRealtimeEligibilityEvidenceRepository evidence,RoleClaimCommitCapabilityGate commitCapabilityGate){
        this.tasks=tasks;this.pools=pools;this.members=members;this.executions=executions;this.instances=instances;
        this.claims=claims;this.audits=audits;this.sodPolicy=sodPolicy;this.ids=ids;this.clock=clock;
        this.runtimeGate=runtimeGate;this.admissionGate=admissionGate;this.evidence=evidence;this.commitCapabilityGate=commitCapabilityGate;
    }

    @Transactional
    public TaskClaimResult claim(Long taskId,RoleClaimRuntimeRequest request,
            SecurityPrincipal principal,String traceId){
        positive(taskId,"taskId"); String key=text(request.claim().idempotencyKey(),"idempotencyKey",128);
        Instant instant=Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime now=LocalDateTime.ofInstant(instant,ZoneOffset.UTC);
        WorkflowTask task=tasks.findByIdForUpdate(taskId).orElseThrow(()->error("B2674","workflow task does not exist"));
        TaskClaim replay=claims.findByTaskIdAndIdempotencyKey(taskId,key).orElse(null);
        if(replay!=null){if(!replay.candidateUserId().equals(principal.userId()))throw error("B2679","idempotency key belongs to another claimant");return TaskClaimResult.from(replay,true);}
        if(task.status()!=WorkflowTask.Status.PENDING)throw error("B2672","workflow task is not claimable");
        CandidatePool pool=pools.findByTaskIdForUpdate(taskId).orElseThrow(()->error("B2674","Candidate Pool does not exist"));
        if(WorkflowClaimRuntimeMode.resolve(task,pool)!=WorkflowClaimRuntimeMode.ROLE_CLAIM)throw error("B5241","task is not ROLE_CLAIM runtime");
        var member=members.findByPoolIdAndUserIdForUpdate(pool.id(),principal.userId())
                .orElseThrow(()->error("B2673","current user is not a frozen Candidate"));
        if(member.status()!=CandidateMemberStatus.INCLUDED)throw error("B2673","frozen Candidate is not eligible");
        WorkflowNodeExecution execution=executions.findByIdForUpdate(task.nodeExecutionId()).orElseThrow(()->error("B2674","node execution does not exist"));
        WorkflowInstance instance=instances.findByIdForUpdate(task.instanceId()).orElseThrow(()->error("B2674","workflow instance does not exist"));
        ownership(task,pool,execution,instance);
        if(execution.status()!=WorkflowNodeExecution.Status.ACTIVE)throw error("B2672","node execution is not ACTIVE");
        if(instance.status()!=WorkflowInstance.Status.RUNNING)throw error("B2672","workflow instance is not RUNNING");
        var bundle=request.evidence(); RoleRealtimeEligibilityPersistenceCanonical.verify(bundle);
        var header=bundle.header(); evidenceOwnership(header,request.verificationToken(),task,pool,member.id(),principal.userId(),instant);
        var admission=admissionGate.verify(new RoleClaimAdmissionGate.Facts(instance.id(),instance.versionId(),task.nodeId(),
                pool.resolverCode(),pool.resolverVersion(),pool.contractHash().value(),header.runtimeBindingHash(),
                header.claimDirectoryRevision(),instance.enterpriseId(),instant));
        if(!admission.allowed())throw error("B5244",admission.reasonCode());
        if(!runtimeGate.allows(new RoleClaimRuntimeGate.RoleClaimGateContext(instance.id(),instance.enterpriseId(),
                instance.definitionId(),instance.versionId(),task.nodeId(),header.organizationId(),
                header.roleCode(),pool.resolverCode(),pool.resolverVersion())))throw error("B5240","ROLE_RUNTIME_DISABLED_OR_OUTSIDE_CANARY");
        var stored=evidence.findByIdForUpdate(header.id()).orElse(null);
        if(stored==null){evidence.insert(bundle);stored=header;}
        else if(!stored.persistenceHash().equals(header.persistenceHash()))throw error("B5242","eligibility evidence idempotency payload mismatch");
        var latest=evidence.findLatestEvent(header.id()).orElseThrow(()->error("B5242","eligibility evidence lifecycle is missing"));
        if("PREPARED".equals(latest.eventType()))latest=append(latest,"VERIFIED",null,"ELIGIBLE",principal,key,instant);
        if(!"VERIFIED".equals(latest.eventType()))throw error("B5243","eligibility evidence is not consumable");
        String scope=dataScope(instance,principal);
        var ctx=new TaskClaimContext(task.id(),instance.id(),execution.id(),principal.userId(),principal.orgId(),
                instance.initiatorUserId(),instance.initiatorOrgId(),instance.businessType(),instance.businessKey());
        var sod=sodPolicy.evaluate(ctx);if(!sod.allowed())throw error("B2676",sod.reasonCode());
        var currentCapabilities=commitCapabilityGate.verify(new RoleClaimCommitCapabilityGate.Facts(instance.id(),
                instance.enterpriseId(),instance.definitionId(),instance.versionId(),task.nodeId(),instance.businessType(),
                instance.businessKey(),header.organizationId(),header.roleCode(),instance.initiatorUserId(),instance.initiatorOrgId(),principal,instant));
        if(!currentCapabilities.allowed())throw error("B5245",currentCapabilities.reasonCode());
        CandidatePool claimedPool=pool.claimed(now);WorkflowTask claimedTask=task.claim(principal.userId(),now);
        long claimId=ids.nextId();TaskClaim claim=TaskClaim.roleClaimed(claimId,task.id(),pool.id(),member.id(),instance.id(),
                execution.id(),principal.userId(),now,key,traceId,header.eligibilityHash(),"ELIGIBLE:"+header.policyVersion(),scope,
                sod.policyCode()+":"+sod.policyVersion()+":"+sod.reasonCode(),task.version(),header.id(),header.persistenceHash(),
                admission.admissionId());
        TaskClaim persisted=claims.insert(claim);
        if(!tasks.claim(claimedTask,task.version()))throw error("B2679","workflow task was claimed concurrently");
        if(!pools.claim(claimedPool,pool.version()))throw error("B2679","Candidate Pool was claimed concurrently");
        audits.appendSuccess(ids.nextId(),persisted);
        append(latest,"CONSUMED",persisted.id(),"CLAIMED",principal,key,instant);
        return TaskClaimResult.from(claim,false);
    }

    private RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent append(
            RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent previous,String type,Long claimId,
            String reason,SecurityPrincipal principal,String key,Instant now){
        var draft=new RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent(ids.nextId(),previous.evidenceRowId(),
                previous.sequenceNo()+1,type,claimId,reason,previous.eventHash(),"0".repeat(64),now,
                principal.userId().toString(),key);
        var event=new RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent(draft.id(),draft.evidenceRowId(),draft.sequenceNo(),
                type,claimId,reason,draft.previousEventHash(),RoleRealtimeEligibilityPersistenceCanonical.eventHash(draft),now,
                draft.operatorId(),key);evidence.appendEvent(event);return event;
    }
    private void evidenceOwnership(RoleRealtimeEligibilityPersistenceBundle.Header h,
            RealtimeEligibilityVerificationToken token,WorkflowTask task,CandidatePool pool,Long memberId,Long claimant,Instant now){
        if(h.taskId()!=task.id()||h.instanceId()!=task.instanceId()||h.nodeExecutionId()!=task.nodeExecutionId()
                ||h.nodeId()!=task.nodeId()||h.definitionVersionId()!=task.versionId()||h.candidatePoolId()!=pool.id()
                ||h.candidateMemberId()!=memberId||h.candidateUserId()!=claimant||token.taskId()!=task.id()
                ||token.candidateUserId()!=claimant||token.taskVersion()!=task.version()
                ||!token.candidatePoolHash().equals(pool.poolHash().value())||!token.candidatePoolHash().equals(h.candidatePoolHash())
                ||!token.runtimeBindingHash().equals(h.runtimeBindingHash())||!token.eligibilityHash().equals(h.eligibilityHash())
                ||!token.revisionFence().candidateRevision().equals(h.candidateDirectoryRevision())
                ||!token.revisionFence().claimRevision().equals(h.claimDirectoryRevision()))throw error("B2678","ROLE Claim evidence ownership or hash chain is inconsistent");
        if(!"ELIGIBLE".equals(h.decision())||!h.directoryComplete()||h.validatorCount()!=27||h.capabilityCount()!=10
                ||!now.isBefore(h.expiresAt())||now.isBefore(h.verifiedAt()))throw error("B5243","ROLE Claim evidence is incomplete or expired");
    }
    private void ownership(WorkflowTask t,CandidatePool p,WorkflowNodeExecution e,WorkflowInstance i){
        if(!t.id().equals(p.taskId())||!t.instanceId().equals(p.instanceId())||!t.nodeExecutionId().equals(p.nodeExecutionId())
                ||!t.instanceId().equals(e.instanceId())||!t.nodeId().equals(e.nodeId())||!t.instanceId().equals(i.id())
                ||!t.versionId().equals(i.versionId()))throw error("B2678","Claim ownership chain is inconsistent");}
    private String dataScope(WorkflowInstance i,SecurityPrincipal p){boolean allowed=p.allDataScope()||i.initiatorUserId().equals(p.userId())
                ||(p.allowedOrgIds()!=null&&p.allowedOrgIds().contains(i.initiatorOrgId()));if(!allowed)throw error("B2677","workflow task is outside current data scope");return p.allDataScope()?"ALLOW:ALL":"ALLOW:SCOPED";}
    private static BusinessException error(String c,String m){return new BusinessException(c,m);} private static void positive(Long v,String f){if(v==null||v<=0)throw error("B2670",f+" must be positive");}
    private static String text(String v,String f,int max){if(v==null||v.isBlank()||v.trim().length()>max)throw error("B2670",f+" is invalid");return v.trim();}
}
