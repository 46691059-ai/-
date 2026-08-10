package cn.gov.enterprise.modules.investment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy;
import cn.gov.enterprise.modules.investment.application.service.InvestmentDecisionApplicationService;
import cn.gov.enterprise.modules.investment.application.workflow.WorkflowEvent;
import cn.gov.enterprise.modules.investment.application.workflow.WorkflowGateway;
import cn.gov.enterprise.modules.investment.domain.model.*;
import cn.gov.enterprise.modules.investment.domain.repository.*;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowOutboxEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.*;
import cn.gov.enterprise.modules.investment.infrastructure.workflow.*;
import cn.gov.enterprise.security.CurrentSecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class InvestmentDecisionWorkflowTest {
    @Test
    void submitFreezesExactMaterialVersionsAndEnqueuesOneWorkflowStart() {
        var repo=mock(InvestmentDecisionRepository.class);var ids=mock(InvestmentIdentityGenerator.class);
        var lifecycle=mock(InvestmentLifecycleStagePolicy.class);var security=mock(CurrentSecurityContext.class);
        var outbox=mock(WorkflowOutboxService.class);var gateway=mock(WorkflowGateway.class);
        var service=new InvestmentDecisionApplicationService(repo,ids,lifecycle,security,
                new WorkflowProperties("http://workflow","service","callback","decision",1),outbox,gateway);
        var decision=new InvestmentDecisionCase(10L,20L,"D-1","重大投资",InvestmentDecisionCase.Status.DRAFT,null,0);
        when(repo.findByIdForUpdate(10L)).thenReturn(Optional.of(decision));
        when(repo.requireFrozenMaterials(20L)).thenReturn(OptionalMaterials.valid());
        when(repo.nextSnapshotVersion(10L)).thenReturn(1);when(repo.nextAttemptNo(10L)).thenReturn(1);
        when(ids.nextId()).thenReturn(101L,102L);when(security.userId()).thenReturn(7L);

        var result=service.submitDecision(10L);

        var snapshot=ArgumentCaptor.forClass(DecisionSnapshot.class);verify(repo).saveSnapshot(snapshot.capture());
        assertThat(snapshot.getValue().schemeVersionId()).isEqualTo(301L);
        assertThat(snapshot.getValue().snapshotHash()).hasSize(64);
        verify(outbox,times(1)).enqueueStart(eq(10L),eq(102L),any());
        assertThat(result.duplicate()).isFalse();
    }

    @Test
    void duplicateSubmissionReusesCurrentAttemptWithoutCallingWorkflow() {
        var repo=mock(InvestmentDecisionRepository.class);var outbox=mock(WorkflowOutboxService.class);
        var service=new InvestmentDecisionApplicationService(repo,mock(InvestmentIdentityGenerator.class),mock(InvestmentLifecycleStagePolicy.class),mock(CurrentSecurityContext.class),new WorkflowProperties("x","s","c","decision",1),outbox,mock(WorkflowGateway.class));
        var d=new InvestmentDecisionCase(10L,20L,"D-1","重大投资",InvestmentDecisionCase.Status.SUBMITTED,101L,1);
        when(repo.findByIdForUpdate(10L)).thenReturn(Optional.of(d));
        when(repo.findCurrentBinding(10L)).thenReturn(Optional.of(new WorkflowBinding(102L,10L,101L,1,null,"key",WorkflowBinding.Status.STARTING,0,0)));
        assertThat(service.submitDecision(10L).duplicate()).isTrue();
        verify(outbox,never()).enqueueStart(anyLong(),anyLong(),any());
    }

    @Test
    void workflowStartFailureIsRetainedForRetryWithoutExposingPayload() {
        var outboxMapper=mock(WorkflowOutboxMapper.class);var bindingMapper=mock(WorkflowBindingMapper.class);
        var ids=mock(InvestmentIdentityGenerator.class);when(ids.nextId()).thenReturn(99L);
        var gateway=mock(WorkflowGateway.class);when(gateway.start(any())).thenThrow(new IllegalStateException("remote down"));
        var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var stored=new AtomicReference<WorkflowOutboxEntity>();doAnswer(i->{stored.set(i.getArgument(0));return 1;}).when(outboxMapper).insert(any(WorkflowOutboxEntity.class));when(outboxMapper.selectById(99L)).thenAnswer(i->stored.get());
        when(outboxMapper.claim(eq(99L),anyInt(),eq("after-commit"),any(),any())).thenAnswer(i->{
            stored.get().setStatus("PROCESSING");stored.get().setWorkerId("after-commit");return 1;
        });
        var service=new WorkflowOutboxService(outboxMapper,bindingMapper,ids,new ObjectMapper(),gateway,manager);
        var command=new WorkflowGateway.StartCommand("INVESTMENT_DECISION","10","key",1L,2L,"hash",1,"decision",1,7L,Map.of(),"idem","trace");
        service.enqueueStart(10L,20L,command);
        verify(outboxMapper).updateById(any(WorkflowOutboxEntity.class));
        assertThat(stored.get().getStatus()).isEqualTo("FAILED");
        verify(bindingMapper).updateById(argThat((cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowBindingEntity b)->"START_RETRYING".equals(b.getWorkflowStatus())));
    }

    @Test
    void repeatedCallbackIsFilteredByInboxEventId() {
        var inbox=mock(WorkflowInboxMapper.class);var existing=new cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowInboxEntity();existing.setPayloadHash("hash");when(inbox.selectByEventId("evt-1")).thenReturn(existing);
        var decisions=mock(InvestmentDecisionRepository.class);
        var service=new WorkflowInboxService(inbox,mock(WorkflowBindingMapper.class),decisions,mock(InvestmentIdentityGenerator.class));
        var event=new WorkflowEvent("evt-1",1,"PROCESS_STARTED","wf-1",10L,11L,1,null,"7",LocalDateTime.now(),"trace");
        assertThat(service.receive(event,"{}","hash")).isEqualTo(WorkflowInboxService.Result.DUPLICATE);
        verifyNoInteractions(decisions);
    }

    @Test
    void decisionOperationsDeclareDedicatedRbacAuthorities() throws Exception {
        assertPermission("createDecision","investment:decision:create",cn.gov.enterprise.modules.investment.application.command.CreateInvestmentDecisionCommand.class);
        assertPermission("submitDecision","investment:decision:submit",Long.class);
        assertPermission("withdrawDecision","investment:decision:withdraw",Long.class,String.class);
        assertPermission("queryStatus","investment:decision:view",Long.class);
    }
    private static void assertPermission(String method,String authority,Class<?>...types)throws Exception{
        var annotation=InvestmentDecisionApplicationService.class.getMethod(method,types).getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();assertThat(annotation.value()).contains(authority);
    }
    private static final class OptionalMaterials {
        static InvestmentDecisionRepository.DecisionMaterials valid(){return new InvestmentDecisionRepository.DecisionMaterials(301L,"FROZEN","scheme-hash",302L,"FROZEN","RECOMMENDED","feasibility-hash",303L,"FROZEN","PASS",0,"dd-hash",1L);}
    }
}
