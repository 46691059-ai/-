package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.*;
import cn.gov.enterprise.modules.workflow.application.service.CanaryGovernanceApplicationService;
import cn.gov.enterprise.modules.workflow.domain.canary.*;
import cn.gov.enterprise.modules.workflow.domain.repository.*;
import java.time.*;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class CanaryGovernanceApplicationServiceTest {
    private static final Instant NOW=Instant.parse("2026-08-26T00:00:00Z");
    private static final String H="b".repeat(64);
    private static final CanaryScope SCOPE=new CanaryScope(990001,990101,990401,990402,990404,"RC1_TEST_CANARY_APPROVER");
    private static final CanaryApprovalEvidence EVIDENCE=new CanaryApprovalEvidence("1",2,H,H,H,H,"workflow-v1.0.0-rc2",
            "740bee63e79a2744eb06ff693b17e7ed3fdf9375",H);

    @Test void proposeApproveAndEnableAreSeparateCasRevisions() {
        var repository=new CasRepository();var service=service(repository);
        var proposed=service.propose(SCOPE,EVIDENCE,"proposed");
        var approved=service.transition(SCOPE,proposed.revision(),CanaryGovernanceState.APPROVED_NOT_ENABLED,"approver","approved");
        assertThat(approved.state()).isEqualTo(CanaryGovernanceState.APPROVED_NOT_ENABLED);
        assertThat(approved.enabledAt()).isNull();
        assertThatThrownBy(()->service.transition(SCOPE,1,CanaryGovernanceState.ENABLED,"release","stale"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CAS");
        assertThat(service.transition(SCOPE,2,CanaryGovernanceState.ENABLED,"release","enabled").state())
                .isEqualTo(CanaryGovernanceState.ENABLED);
    }

    @Test void concurrentApprovalHasSingleWinner() throws Exception {
        var repository=new CasRepository();var service=service(repository);service.propose(SCOPE,EVIDENCE,"proposed");
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);var successes=new AtomicInteger();
            Callable<Void> action=()->{start.await();try{service.transition(SCOPE,1,CanaryGovernanceState.APPROVED_NOT_ENABLED,"approver","approved");successes.incrementAndGet();}catch(RuntimeException ignored){}return null;};
            Future<Void> one=executor.submit(action);Future<Void> two=executor.submit(action);start.countDown();one.get();two.get();
            assertThat(successes).hasValue(1);
        } finally { executor.shutdownNow(); }
    }

    private static CanaryGovernanceApplicationService service(CasRepository repository) {
        AtomicLong ids=new AtomicLong();WorkflowIdentityGenerator generator=ids::incrementAndGet;
        return new CanaryGovernanceApplicationService(repository,generator,Clock.fixed(NOW,ZoneOffset.UTC));
    }

    private static final class CasRepository implements CanaryGovernanceRepository {
        private final AtomicReference<CanaryGovernanceRecord> current=new AtomicReference<>();
        @Override public void insert(CanaryGovernanceRecord record) {
            CanaryGovernanceRecord expected=current.get();
            if((expected==null&&record.previousRecordId()!=null)||(expected!=null&&!Objects.equals(expected.id(),record.previousRecordId()))
                    ||!current.compareAndSet(expected,record))throw new IllegalStateException("concurrent Canary revision");
        }
        @Override public Optional<CanaryGovernanceRecord> latest(CanaryScope scope,Instant at){
            CanaryGovernanceRecord value=current.get();return value!=null&&value.scope().equals(scope)?Optional.of(value):Optional.empty();
        }
    }
}
