package cn.gov.enterprise.modules.workflow.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.*;
import cn.gov.enterprise.modules.workflow.domain.assignment.*;
import cn.gov.enterprise.modules.workflow.domain.candidate.*;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.claim.*;
import cn.gov.enterprise.modules.workflow.domain.model.*;
import cn.gov.enterprise.modules.workflow.domain.repository.*;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.*;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.*;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleClaimRuntimeIntegrationTransactionTest {
    private static final Instant NOW=Instant.parse("2026-08-20T01:02:03.004Z");
    private static final String HASH="a".repeat(64);
    private final WorkflowTaskRepository tasks=mock(WorkflowTaskRepository.class);
    private final CandidatePoolRepository pools=mock(CandidatePoolRepository.class);
    private final CandidatePoolMemberRepository members=mock(CandidatePoolMemberRepository.class);
    private final WorkflowNodeExecutionRepository executions=mock(WorkflowNodeExecutionRepository.class);
    private final WorkflowInstanceRepository instances=mock(WorkflowInstanceRepository.class);
    private final TaskClaimRepository claims=mock(TaskClaimRepository.class);
    private final TaskClaimAuditRepository audits=mock(TaskClaimAuditRepository.class);
    private final RoleRealtimeEligibilityEvidenceRepository evidence=mock(RoleRealtimeEligibilityEvidenceRepository.class);
    private final WorkflowIdentityGenerator ids=mock(WorkflowIdentityGenerator.class);
    private final SegregationOfDutiesPolicy sod=mock(SegregationOfDutiesPolicy.class);
    private final AtomicLong sequence=new AtomicLong(5000);

    @BeforeEach void setup() {
        when(tasks.findByIdForUpdate(200L)).thenReturn(Optional.of(task()));
        when(pools.findByTaskIdForUpdate(200L)).thenReturn(Optional.of(pool()));
        when(members.findByPoolIdAndUserIdForUpdate(300L,21L)).thenReturn(Optional.of(member()));
        when(executions.findByIdForUpdate(600L)).thenReturn(Optional.of(execution()));
        when(instances.findByIdForUpdate(10L)).thenReturn(Optional.of(instance()));
        when(claims.findByTaskIdAndIdempotencyKey(anyLong(),anyString())).thenReturn(Optional.empty());
        when(claims.insert(any())).thenAnswer(i->i.getArgument(0));
        when(tasks.claim(any(),anyInt())).thenReturn(true); when(pools.claim(any(),anyInt())).thenReturn(true);
        when(sod.evaluate(any())).thenReturn(new SegregationOfDutiesPolicy.Decision(true,"PLATFORM_BUSINESS","V1","ALLOW","ok"));
        when(ids.nextId()).thenAnswer(i->sequence.incrementAndGet());
    }

    @Test void completeRoleClaimBindsAndConsumesExactlyOneEvidence() {
        var bundle=bundle();
        when(evidence.findByIdForUpdate(900L)).thenReturn(Optional.empty());
        when(evidence.findLatestEvent(900L)).thenReturn(Optional.of(bundle.initialEvent()));
        var service=service(c->true);
        var result=service.claim(200L,new RoleClaimRuntimeRequest(command(),token(),bundle),principal(),"trace");
        assertThat(result.taskStatus()).isEqualTo("CLAIMED");
        verify(evidence).insert(bundle); verify(claims).insert(argThat(c->c.eligibilityEvidenceId()==900L));
        verify(audits).appendSuccess(anyLong(),argThat(c->c.eligibilityPersistenceHash().equals(bundle.header().persistenceHash())));
        verify(evidence,times(2)).appendEvent(any());
        verify(tasks).claim(any(),eq(0)); verify(pools).claim(any(),eq(0));
    }

    @Test void disabledGateFailsBeforeEvidenceOrClaimMutation() {
        assertThatThrownBy(()->service(c->false).claim(200L,
                new RoleClaimRuntimeRequest(command(),token(),bundle()),principal(),"trace"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ROLE_RUNTIME_DISABLED");
        verify(evidence,never()).insert(any()); verify(claims,never()).insert(any());
    }

    @Test void auditFailurePreventsConsumedEventFromBeingAppended() {
        var bundle=bundle(); when(evidence.findByIdForUpdate(900L)).thenReturn(Optional.empty());
        when(evidence.findLatestEvent(900L)).thenReturn(Optional.of(bundle.initialEvent()));
        doThrow(new BusinessException("B","audit fail")).when(audits).appendSuccess(anyLong(),any());
        assertThatThrownBy(()->service(c->true).claim(200L,
                new RoleClaimRuntimeRequest(command(),token(),bundle),principal(),"trace"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("audit fail");
        verify(evidence,times(1)).appendEvent(any());
    }

    private RoleClaimRuntimeTransactionService service(RoleClaimRuntimeGate gate) {
        return new RoleClaimRuntimeTransactionService(tasks,pools,members,executions,instances,claims,audits,
                sod,ids,Clock.fixed(NOW,ZoneOffset.UTC),gate,
                facts -> RoleClaimAdmissionGate.Decision.allow("TEST","a".repeat(64)),evidence);
    }
    private ClaimWorkflowTaskCommand command(){return new ClaimWorkflowTaskCommand("idem-role",null,null);}
    private SecurityPrincipal principal(){return new SecurityPrincipal(21L,"u21",30L,Set.of(30L),false,false,0);}
    private RealtimeEligibilityVerificationToken token(){return new RealtimeEligibilityVerificationToken(200,21,0,
            pool().poolHash().value(),HASH,new RealtimeEligibilityRevisionFence("REV-1","REV-1",HASH,HASH,NOW),
            HASH,NOW,NOW.plusSeconds(60));}
    private WorkflowTask task(){return new WorkflowTask(200L,"WFT-200",10L,400L,500L,600L,"REVIEW","Review",1,
            "REVIEW:1:1",null,"{}",WorkflowTask.Status.PENDING,"APPROVE,REJECT",null,null,null,null,null,0,
            WorkflowTask.AssignmentMode.CANDIDATE_POOL);}
    private CandidatePool pool(){return CandidatePool.created(300L,"WCP-300",200L,10L,400L,500L,600L,700L,800L,900L,
            1000L,AssignmentStrategy.Type.ROLE,"ROLE_DIRECTORY_V1","ROLE_DIRECTORY_V1",ResolverContractHash.of(HASH),HASH,
            LocalDateTime.ofInstant(NOW.minusSeconds(60),ZoneOffset.UTC),LocalDateTime.ofInstant(NOW.minusSeconds(60),ZoneOffset.UTC),
            LocalDateTime.ofInstant(NOW.plusSeconds(60),ZoneOffset.UTC),List.of(member()),"frozen").available();}
    private CandidatePoolMember member(){return new CandidatePoolMember(401L,300L,200L,10L,21L,AssignmentStrategy.Type.ROLE,
            "ROLE:APPROVER",30L,null,50L,"frozen",ResolverContractHash.sha256("frozen").value(),1,
            LocalDateTime.ofInstant(NOW.minusSeconds(60),ZoneOffset.UTC),CandidateMemberStatus.INCLUDED,"frozen",0);}
    private WorkflowNodeExecution execution(){return new WorkflowNodeExecution(600L,"WNE-600",10L,400L,500L,"REVIEW","Review",1,
            null,null,WorkflowNodeExecution.Status.ACTIVE,null,LocalDateTime.ofInstant(NOW.minusSeconds(60),ZoneOffset.UTC),
            LocalDateTime.ofInstant(NOW.minusSeconds(30),ZoneOffset.UTC),null,null,null,"trace",0);}
    private WorkflowInstance instance(){var d=new WorkflowDefinition(1L,"TEST","Test","TEST",100L,30L,
            WorkflowDefinition.Status.ACTIVE,400L,null,0);var v=new WorkflowVersion(400L,1L,1,WorkflowVersion.Status.PUBLISHED,
            "1.0","hash",null,LocalDateTime.ofInstant(NOW,ZoneOffset.UTC),null,99L,LocalDateTime.ofInstant(NOW,ZoneOffset.UTC),null,0);
        return WorkflowInstance.running(10L,"WFI-10",d,v,"TEST","1","TEST:1",100L,null,null,1,99L,30L,500L,null,
                "start","request",null,LocalDateTime.ofInstant(NOW.minusSeconds(60),ZoneOffset.UTC));}
    private RoleRealtimeEligibilityPersistenceBundle bundle(){long row=900;var vs=new ArrayList<RoleRealtimeEligibilityPersistenceBundle.ValidatorEvidence>();
        for(int i=1;i<=27;i++){String c="V_"+i;String h=RoleRealtimeEligibilityPersistenceCanonical.validatorHash(i,c,"PASS","OK",NOW);
            vs.add(new RoleRealtimeEligibilityPersistenceBundle.ValidatorEvidence(1000+i,row,c,i,"PASS","OK",h,NOW));}
        var cs=new ArrayList<RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence>();
        for(int i=1;i<=10;i++){String c="C_"+i;String h=RoleRealtimeEligibilityPersistenceCanonical.capabilityHash(c,"V_"+i,"PASS","PASS","P","P",NOW,NOW.plusSeconds(60));
            cs.add(new RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence(2000+i,row,c,"V_"+i,"PASS","PASS","P","P",h,NOW,NOW.plusSeconds(60)));}
        String vr=RoleRealtimeEligibilityPersistenceCanonical.validatorRoot(vs),cr=RoleRealtimeEligibilityPersistenceCanonical.capabilityRoot(cs);
        var draft=header(vr,cr,HASH);String ph=RoleRealtimeEligibilityPersistenceCanonical.persistenceHash(draft);var h=header(vr,cr,ph);
        var ed=new RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent(3000,row,1,"PREPARED",null,"OK",null,HASH,NOW,"21","idem-role");
        var e=new RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent(3000,row,1,"PREPARED",null,"OK",null,
                RoleRealtimeEligibilityPersistenceCanonical.eventHash(ed),NOW,"21","idem-role");return new RoleRealtimeEligibilityPersistenceBundle(h,vs,cs,e);}
    private RoleRealtimeEligibilityPersistenceBundle.Header header(String vr,String cr,String ph){return new RoleRealtimeEligibilityPersistenceBundle.Header(
            900,"E-900","REQ-900","corr","idem-role",1,"corr",10,400,500,600,200,300,401,21,700,800,900,
            "ROLE_A","30",pool().poolHash().value(),HASH,HASH,"REV-1","REV-1",HASH,HASH,true,NOW,NOW,"ELIGIBLE",27,27,10,
            vr,cr,ph,"POLICY_V1","CONTRACT_TEST",NOW,NOW,NOW.plusSeconds(60));}
}
