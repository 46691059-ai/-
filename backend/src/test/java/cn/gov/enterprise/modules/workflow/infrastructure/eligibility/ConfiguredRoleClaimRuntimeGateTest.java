package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.gov.enterprise.modules.workflow.domain.canary.CanaryScope;
import cn.gov.enterprise.modules.workflow.domain.claim.RoleClaimRuntimeGate.RoleClaimGateContext;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfiguredRoleClaimRuntimeGateTest {
    private static final RoleClaimGateContext CANARY = new RoleClaimGateContext(
            1,10,20,30,40,"50","APPROVER","ROLE_DIRECTORY","ROLE_DIRECTORY_V1");
    private static final String HASH="a".repeat(64);

    @Test void exactSixDimensionalScopeAndFeatureFlagsMayPass() {
        var gate=new ConfiguredRoleClaimRuntimeGate(controls("ON"),(scope,at)->scope.equals(
                new CanaryScope(10,50,20,30,40,"APPROVER")));
        assertThat(gate.allows(CANARY)).isTrue();
    }

    @Test void scopeMismatchAndMissingFeatureMustFailClosed() {
        assertThat(new ConfiguredRoleClaimRuntimeGate(controls("ON"),(scope,at)->false).allows(CANARY)).isFalse();
        assertThat(new ConfiguredRoleClaimRuntimeGate(controls(null),(scope,at)->true).allows(CANARY)).isFalse();
        var invalidOrg=new RoleClaimGateContext(1,10,20,30,40,"ORG","APPROVER","ROLE_DIRECTORY","ROLE_DIRECTORY_V1");
        assertThat(new ConfiguredRoleClaimRuntimeGate(controls("ON"),(scope,at)->true).allows(invalidOrg)).isFalse();
    }

    private static RoleRuntimeGovernanceControlStore controls(String decision) {
        var store=mock(RoleRuntimeGovernanceControlStore.class);
        if(decision!=null)when(store.latest(eq("FEATURE_FLAG"),anyString(),any(Instant.class)))
                .thenReturn(Optional.of(new RoleRuntimeGovernanceControlStore.Control(decision,1,HASH,HASH,Instant.EPOCH,null)));
        else when(store.latest(eq("FEATURE_FLAG"),anyString(),any(Instant.class))).thenReturn(Optional.empty());
        return store;
    }
}
