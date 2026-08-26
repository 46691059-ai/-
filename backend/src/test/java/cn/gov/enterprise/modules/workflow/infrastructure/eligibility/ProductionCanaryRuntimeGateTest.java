package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.gov.enterprise.modules.workflow.domain.canary.*;
import cn.gov.enterprise.modules.workflow.domain.repository.CanaryGovernanceRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductionCanaryRuntimeGateTest {
    private static final Instant NOW=Instant.parse("2026-08-25T00:00:00Z");
    private static final String H="a".repeat(64);
    private static final CanaryScope S=new CanaryScope(1,2,3,4,5,"ROLE");

    @Test void enabledExactScopeRequiresIndependentRuntimeAndAllKillSwitches() {
        assertThat(gate(true,state(CanaryGovernanceState.ENABLED),"ALLOW").allows(S,NOW)).isTrue();
        assertThat(gate(false,state(CanaryGovernanceState.ENABLED),"ALLOW").allows(S,NOW)).isFalse();
        assertThat(gate(true,state(CanaryGovernanceState.PROPOSED),"ALLOW").allows(S,NOW)).isFalse();
        assertThat(gate(true,state(CanaryGovernanceState.APPROVED_NOT_ENABLED),"ALLOW").allows(S,NOW)).isFalse();
        assertThat(gate(true,state(CanaryGovernanceState.SUSPENDED),"ALLOW").allows(S,NOW)).isFalse();
        assertThat(gate(true,state(CanaryGovernanceState.REVOKED),"ALLOW").allows(S,NOW)).isFalse();
        assertThat(gate(true,Optional.empty(),"ALLOW").allows(S,NOW)).isFalse();
        assertThat(gate(true,state(CanaryGovernanceState.ENABLED),"STOP_NEW_AND_CLAIM").allows(S,NOW)).isFalse();
    }

    @Test void anyScopeEscapeIsDeniedByExactRepositoryLookup() {
        CanaryGovernanceRepository repository=mock(CanaryGovernanceRepository.class);
        when(repository.latest(eq(S),eq(NOW))).thenReturn(state(CanaryGovernanceState.ENABLED));
        var gate=new ProductionCanaryRuntimeGate(repository,controls("ALLOW"),true);
        assertThat(gate.allows(new CanaryScope(9,2,3,4,5,"ROLE"),NOW)).isFalse();
        assertThat(gate.allows(new CanaryScope(1,9,3,4,5,"ROLE"),NOW)).isFalse();
        assertThat(gate.allows(new CanaryScope(1,2,9,4,5,"ROLE"),NOW)).isFalse();
        assertThat(gate.allows(new CanaryScope(1,2,3,9,5,"ROLE"),NOW)).isFalse();
        assertThat(gate.allows(new CanaryScope(1,2,3,4,9,"ROLE"),NOW)).isFalse();
        assertThat(gate.allows(new CanaryScope(1,2,3,4,5,"OTHER"),NOW)).isFalse();
    }

    private static ProductionCanaryRuntimeGate gate(boolean enabled,Optional<CanaryGovernanceRecord> record,String kill){
        CanaryGovernanceRepository repository=mock(CanaryGovernanceRepository.class);when(repository.latest(any(),any())).thenReturn(record);
        return new ProductionCanaryRuntimeGate(repository,controls(kill),enabled);
    }
    private static RoleRuntimeGovernanceControlStore controls(String decision){
        var store=mock(RoleRuntimeGovernanceControlStore.class);when(store.latest(eq("KILL_SWITCH"),anyString(),any()))
                .thenReturn(Optional.of(new RoleRuntimeGovernanceControlStore.Control(decision,1,H,H,NOW.minusSeconds(1),null)));
        when(store.latest(eq("FEATURE_FLAG"),anyString(),any())).thenReturn(Optional.of(
                new RoleRuntimeGovernanceControlStore.Control("ON",1,H,H,NOW.minusSeconds(1),null)));return store;
    }
    private static Optional<CanaryGovernanceRecord> state(CanaryGovernanceState state){
        var evidence=new CanaryApprovalEvidence("1",2,H,H,H,H,"tag","740bee63e79a2744eb06ff693b17e7ed3fdf9375",H);
        var r=CanaryGovernanceRecord.proposed(1,S,evidence,"p",NOW.minusSeconds(10));long id=2;
        if(state!=CanaryGovernanceState.PROPOSED)r=r.transition(id++,state==CanaryGovernanceState.REVOKED?state:CanaryGovernanceState.APPROVED_NOT_ENABLED,"a","x",NOW.minusSeconds(9));
        if(state==CanaryGovernanceState.ENABLED||state==CanaryGovernanceState.SUSPENDED)r=r.transition(id++,CanaryGovernanceState.ENABLED,"a","x",NOW.minusSeconds(8));
        if(state==CanaryGovernanceState.SUSPENDED)r=r.transition(id,CanaryGovernanceState.SUSPENDED,"a","x",NOW.minusSeconds(7));
        return Optional.of(r);
    }
}
