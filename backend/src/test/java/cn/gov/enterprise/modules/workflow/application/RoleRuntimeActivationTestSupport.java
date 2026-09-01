package cn.gov.enterprise.modules.workflow.application;

import static org.mockito.Mockito.*;
import cn.gov.enterprise.modules.workflow.application.command.*;
import cn.gov.enterprise.modules.workflow.application.service.RoleRuntimeActivationApplicationService;
import cn.gov.enterprise.modules.workflow.domain.canary.*;
import cn.gov.enterprise.modules.workflow.domain.repository.*;
import cn.gov.enterprise.modules.workflow.domain.role.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

final class RoleRuntimeActivationTestSupport {
    static final CanaryScope SCOPE=new CanaryScope(990001,990101,990401,990402,990404,"RC1_TEST_CANARY_APPROVER");
    static final Instant NOW=Instant.parse("2026-08-31T10:00:00Z");
    static final String H="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static RoleRuntimeActivationCommand command(){return new RoleRuntimeActivationCommand(SCOPE,"evt-1","auth-1",RoleRuntimeActivationEvent.AUTHORIZATION_TYPE,
            "1111111111111111111111111111111111111111",RoleRuntimeActivationApplicationService.DESIGN_COMMIT,
            RoleRuntimeActivationApplicationService.OBSERVATION_COMMIT,RoleRuntimeActivationApplicationService.ENABLEMENT_EVIDENCE_COMMIT,
            RoleRuntimeActivationApplicationService.RELEASE_COMMIT,RoleRuntimeActivationApplicationService.RELEASE_TAG,H,
            RoleRuntimeActivationApplicationService.DIRECTORY_HASH,RoleRuntimeActivationApplicationService.VERSION_HASH,
            RoleRuntimeActivationApplicationService.MANIFEST_HASH,RoleRuntimeActivationApplicationService.CONTENT_HASH,
            RoleRuntimeActivationApplicationService.STRUCTURAL_HASH,"HUMAN","operator");}
    static Fixture fixture(){
        var events=new MemoryEvents();var requests=mock(PersistentActivationRequestRepository.class);
        var decisions=mock(PersistentActivationDecisionRepository.class);var evidence=mock(ActivationEvidenceRepository.class);
        var canary=mock(CanaryGovernanceRepository.class);var ids=mock(WorkflowIdentityGenerator.class);
        var request=mock(PersistentActivationRequest.class);when(request.status()).thenReturn(PersistentActivationStatus.PERSISTED);
        when(request.approvalEvidenceHash()).thenReturn(H);when(requests.findByActivationId("auth-1")).thenReturn(Optional.of(request));
        List<PersistentActivationDecision> approvals=new ArrayList<>();
        for(String role:List.of("BUSINESS_OWNER","SECURITY_AUDIT","RELEASE_APPROVER")){var d=mock(PersistentActivationDecision.class);when(d.decision()).thenReturn("APPROVE");when(d.approverType()).thenReturn(role);approvals.add(d);}
        when(decisions.findByActivationId("auth-1")).thenReturn(approvals);
        when(evidence.findByActivationId("auth-1")).thenReturn(List.of(mock(ActivationEvidenceRecord.class)));
        var ce=new CanaryApprovalEvidence("dir",1,RoleRuntimeActivationApplicationService.DIRECTORY_HASH,
                RoleRuntimeActivationApplicationService.VERSION_HASH,RoleRuntimeActivationApplicationService.MANIFEST_HASH,
                RoleRuntimeActivationApplicationService.CONTENT_HASH,RoleRuntimeActivationApplicationService.RELEASE_TAG,
                RoleRuntimeActivationApplicationService.RELEASE_COMMIT,RoleRuntimeActivationApplicationService.STRUCTURAL_HASH);
        var record=new CanaryGovernanceRecord(3L,2L,SCOPE,CanaryGovernanceState.ENABLED,3,ce,"approver",NOW.minusSeconds(20),"enabler",NOW.minusSeconds(10),null,null,"enabled",NOW.minusSeconds(10),null);
        when(canary.latest(eq(SCOPE),any())).thenReturn(Optional.of(record));when(canary.countByExactScopeAndState(SCOPE,CanaryGovernanceState.ENABLED)).thenReturn(1L);
        when(ids.nextId()).thenReturn(9001L);
        return new Fixture(events,requests,decisions,evidence,canary,ids);
    }
    record Fixture(MemoryEvents events,PersistentActivationRequestRepository requests,PersistentActivationDecisionRepository decisions,
            ActivationEvidenceRepository evidence,CanaryGovernanceRepository canary,WorkflowIdentityGenerator ids){
        RoleRuntimeActivationApplicationService service(){return new RoleRuntimeActivationApplicationService(events,requests,decisions,evidence,canary,ids,"STOP_NEW_AND_CLAIM",Clock.fixed(NOW,ZoneOffset.UTC));}
        RoleRuntimeActivationApplicationService service(String killSwitch){return new RoleRuntimeActivationApplicationService(events,requests,decisions,evidence,canary,ids,killSwitch,Clock.fixed(NOW,ZoneOffset.UTC));}
    }
    static final class MemoryEvents implements RoleRuntimeActivationEventRepository{
        final AtomicReference<RoleRuntimeActivationEvent> value=new AtomicReference<>();
        volatile RuntimeException appendFailure;
        public Optional<RoleRuntimeActivationEvent> findByExactScope(CanaryScope scope){return Optional.ofNullable(value.get()).filter(e->e.scope().equals(scope));}
        public long countByExactScope(CanaryScope scope){return findByExactScope(scope).isPresent()?1:0;}
        public boolean append(RoleRuntimeActivationEvent event){if(appendFailure!=null)throw appendFailure;return value.compareAndSet(null,event);}
    }
    private RoleRuntimeActivationTestSupport(){}
}
