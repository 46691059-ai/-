package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.domain.canary.CanaryRuntimeGate;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryScope;
import cn.gov.enterprise.modules.workflow.domain.repository.CanaryGovernanceRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "workflow.role-runtime.claim-enabled=true",
        "spring.data.redis.repositories.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "V2624_MYSQL_URL", matches = ".+")
class V2624CanaryGovernanceRealMysqlTest {
    private static final Instant AT = Instant.parse("2026-08-31T08:00:00Z");
    private static final CanaryScope SCOPE = new CanaryScope(
            990001, 990101, 990401, 990402, 990404, "RC1_TEST_CANARY_APPROVER");

    @Autowired private CanaryRuntimeGate gate;
    @Autowired private CanaryGovernanceRepository repository;
    @Autowired private RoleRuntimeGovernanceControlStore controls;

    @Test
    void realPersistenceAndRuntimeGateRequireExactScopeAndIndependentRuntimeEnablement() {
        assertThat(repository.latest(SCOPE, AT)).get()
                .extracting(record -> record.state().name()).isEqualTo("APPROVED_NOT_ENABLED");
        assertThat(gate.allows(SCOPE, AT)).isFalse();
        assertThat(gate.allows(new CanaryScope(999999, 990101, 990401, 990402, 990404,
                "RC1_TEST_CANARY_APPROVER"), AT)).isFalse();
        assertThat(gate.allows(new CanaryScope(990001, 999999, 990401, 990402, 990404,
                "RC1_TEST_CANARY_APPROVER"), AT)).isFalse();
        assertThat(gate.allows(new CanaryScope(990001, 990101, 999999, 990402, 990404,
                "RC1_TEST_CANARY_APPROVER"), AT)).isFalse();
        assertThat(gate.allows(new CanaryScope(990001, 990101, 990401, 999999, 990404,
                "RC1_TEST_CANARY_APPROVER"), AT)).isFalse();
        assertThat(gate.allows(new CanaryScope(990001, 990101, 990401, 990402, 999999,
                "RC1_TEST_CANARY_APPROVER"), AT)).isFalse();
        assertThat(gate.allows(new CanaryScope(990001, 990101, 990401, 990402, 990404,
                "RC1_TEST_CANARY_OTHER"), AT)).isFalse();

        var runtimeDisabled = new ProductionCanaryRuntimeGate(repository, controls, false);
        assertThat(runtimeDisabled.allows(SCOPE, AT)).isFalse();
    }
}
