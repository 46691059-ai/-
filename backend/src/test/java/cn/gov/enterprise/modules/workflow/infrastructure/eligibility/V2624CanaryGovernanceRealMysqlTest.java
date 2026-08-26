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
    private static final Instant AT = Instant.parse("2026-08-26T02:30:00Z");
    private static final CanaryScope SCOPE = new CanaryScope(
            991001, 991101, 991401, 991402, 991404, "RC2_CANARY_GATE_TEST");

    @Autowired private CanaryRuntimeGate gate;
    @Autowired private CanaryGovernanceRepository repository;
    @Autowired private RoleRuntimeGovernanceControlStore controls;

    @Test
    void realPersistenceAndRuntimeGateRequireExactScopeAndIndependentRuntimeEnablement() {
        assertThat(repository.latest(SCOPE, AT)).get()
                .extracting(record -> record.state().name()).isEqualTo("ENABLED");
        assertThat(gate.allows(SCOPE, AT)).isTrue();
        assertThat(gate.allows(new CanaryScope(999999, 991101, 991401, 991402, 991404,
                "RC2_CANARY_GATE_TEST"), AT)).isFalse();
        assertThat(gate.allows(new CanaryScope(991001, 999999, 991401, 991402, 991404,
                "RC2_CANARY_GATE_TEST"), AT)).isFalse();
        assertThat(gate.allows(new CanaryScope(991001, 991101, 999999, 991402, 991404,
                "RC2_CANARY_GATE_TEST"), AT)).isFalse();
        assertThat(gate.allows(new CanaryScope(991001, 991101, 991401, 999999, 991404,
                "RC2_CANARY_GATE_TEST"), AT)).isFalse();
        assertThat(gate.allows(new CanaryScope(991001, 991101, 991401, 991402, 999999,
                "RC2_CANARY_GATE_TEST"), AT)).isFalse();
        assertThat(gate.allows(new CanaryScope(991001, 991101, 991401, 991402, 991404,
                "RC2_CANARY_GATE_OTHER"), AT)).isFalse();

        var runtimeDisabled = new ProductionCanaryRuntimeGate(repository, controls, false);
        assertThat(runtimeDisabled.allows(SCOPE, AT)).isFalse();
    }
}
