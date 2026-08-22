package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.domain.claim.RoleClaimRuntimeGate.RoleClaimGateContext;
import org.junit.jupiter.api.Test;

class ConfiguredRoleClaimRuntimeGateTest {
    private static final RoleClaimGateContext CANARY =
            new RoleClaimGateContext(1, 10, 20, 30, 40, "ORG", "ROLE_DIRECTORY_V1", "ROLE_DIRECTORY_V1");

    @Test void defaultDisabledAndStopSwitchMustFailClosed() {
        assertThat(gate(false, "ALLOW", "10", "20", "30", "40").allows(CANARY)).isFalse();
        assertThat(gate(true, "STOP_NEW_AND_CLAIM", "10", "20", "30", "40").allows(CANARY)).isFalse();
    }

    @Test void exactEnterpriseVersionAndNodeCanaryMayPass() {
        assertThat(gate(true, "ALLOW", "10", "20", "30", "40").allows(CANARY)).isTrue();
    }

    @Test void everyMissingOrDriftedCanaryDimensionMustFailClosed() {
        assertThat(gate(true, "ALLOW", "", "20", "30", "40").allows(CANARY)).isFalse();
        assertThat(gate(true, "ALLOW", "11", "20", "30", "40").allows(CANARY)).isFalse();
        assertThat(gate(true, "ALLOW", "10", "21", "30", "40").allows(CANARY)).isFalse();
        assertThat(gate(true, "ALLOW", "10", "20", "31", "40").allows(CANARY)).isFalse();
        assertThat(gate(true, "ALLOW", "10", "20", "30", "41").allows(CANARY)).isFalse();
    }

    private static ConfiguredRoleClaimRuntimeGate gate(boolean enabled, String killSwitch,
            String enterpriseId, String definitionId, String versionId, String nodeId) {
        return new ConfiguredRoleClaimRuntimeGate(enabled, killSwitch, enterpriseId, definitionId, versionId, nodeId);
    }
}
