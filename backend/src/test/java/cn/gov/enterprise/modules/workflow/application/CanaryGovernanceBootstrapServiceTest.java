package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.application.service.*;
import cn.gov.enterprise.modules.workflow.domain.canary.*;
import cn.gov.enterprise.modules.workflow.domain.repository.CanaryGovernanceRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CanaryGovernanceBootstrapServiceTest {
    private static final Instant NOW=Instant.parse("2026-08-31T07:00:00Z");
    private static final String H="a".repeat(64), S="b".repeat(40);
    private static final CanaryScope SCOPE=new CanaryScope(990001,990101,990401,990402,
            990404,"RC1_TEST_CANARY_APPROVER");

    @Test void exactFrozenBootstrapCreatesProposedThenApprovedAndReplayIsIdempotent(){
        Repo repo=new Repo();var service=service(repo);
        assertThat(service.bootstrapApproved(command(SCOPE))).isEqualTo(CanaryGovernanceBootstrapResult.CREATED);
        assertThat(service.bootstrapApproved(command(SCOPE))).isEqualTo(CanaryGovernanceBootstrapResult.ALREADY_EXISTS);
        assertThat(repo.rows).hasSize(2);
        assertThat(repo.rows).extracting(CanaryGovernanceRecord::state)
                .containsExactly(CanaryGovernanceState.PROPOSED,CanaryGovernanceState.APPROVED_NOT_ENABLED);
        var approved=repo.rows.get(1);
        assertThat(approved.scope()).isEqualTo(SCOPE);
        assertThat(approved.evidence()).isEqualTo(command(SCOPE).evidence());
        assertThat(approved.reason()).isEqualTo(command(SCOPE).bindingReason());
        assertThat(approved.enabledAt()).isNull();
        assertThat(approved.enablementActor()).isNull();
    }

    @Test void differentBindingAndCrossScopeCannotReplayExistingAggregate(){
        Repo repo=new Repo();var service=service(repo);service.bootstrapApproved(command(SCOPE));
        var other=new CanaryScope(990001,990101,990401,990402,990405,"RC1_TEST_CANARY_APPROVER");
        assertThat(repo.latest(other,NOW)).isEmpty();
        var changed=new CanaryGovernanceBootstrapCommand(SCOPE,command(SCOPE).evidence(),
                "c".repeat(40),S,S,S,S,S,"human");
        assertThatThrownBy(()->service.bootstrapApproved(changed))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void concurrentBootstrapHasOneWinnerAndOneExactLedger(){
        Repo repo=new Repo();var service=service(repo);var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var calls=new ArrayList<Future<?>>();
            for(int i=0;i<2;i++)calls.add(executor.submit(()->{start.await();try{service.bootstrapApproved(command(SCOPE));}catch(RuntimeException ignored){}return null;}));
            start.countDown();calls.forEach(f->{try{f.get();}catch(Exception e){throw new AssertionError(e);}});
        }
        assertThat(repo.rows).hasSize(2);
        assertThat(repo.rows.get(1).state()).isEqualTo(CanaryGovernanceState.APPROVED_NOT_ENABLED);
    }

    private static CanaryGovernanceApplicationService service(Repo repo){
        AtomicLong ids=new AtomicLong();return new CanaryGovernanceApplicationService(repo,ids::incrementAndGet,
                Clock.fixed(NOW,ZoneOffset.UTC));
    }
    private static CanaryGovernanceBootstrapCommand command(CanaryScope scope){
        var evidence=new CanaryApprovalEvidence("1",2,H,H,H,H,"workflow-v1.0.0-rc2.1",S,H);
        return new CanaryGovernanceBootstrapCommand(scope,evidence,S,S,S,S,S,S,"human");
    }
    private static final class Repo implements CanaryGovernanceRepository{
        private final List<CanaryGovernanceRecord> rows=Collections.synchronizedList(new ArrayList<>());
        public synchronized void insert(CanaryGovernanceRecord r){
            if(rows.stream().anyMatch(x->x.scope().equals(r.scope())&&x.revision()==r.revision()))throw new IllegalStateException("duplicate");
            rows.add(r);
        }
        public synchronized Optional<CanaryGovernanceRecord> latest(CanaryScope s,Instant at){
            return rows.stream().filter(x->x.scope().equals(s)&&!x.effectiveFrom().isAfter(at))
                    .max(Comparator.comparingLong(CanaryGovernanceRecord::revision));
        }
    }
}
