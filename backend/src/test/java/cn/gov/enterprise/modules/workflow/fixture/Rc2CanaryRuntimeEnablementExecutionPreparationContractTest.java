package cn.gov.enterprise.modules.workflow.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.application.service.CanaryGovernanceApplicationService;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryApprovalEvidence;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryGovernanceRecord;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryGovernanceState;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryScope;
import cn.gov.enterprise.modules.workflow.domain.repository.CanaryGovernanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCanonicalHashSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class Rc2CanaryRuntimeEnablementExecutionPreparationContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path FIXTURE = Rc2ControlledCanaryFixtureContract.REPOSITORY_ROOT
            .resolve("database/test-fixtures/rc2");
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final CanaryScope SCOPE = new CanaryScope(990001, 990101, 990401,
            990402, 990404, "RC1_TEST_CANARY_APPROVER");

    @Test
    void templateUniquelyBindsRealTargetAuthorizationReleaseEvidenceAndOperationKey()
            throws Exception {
        JsonNode template = read("rc2-canary-runtime-enablement-execution-v1.json");
        JsonNode authorization = read("rc2-canary-enablement-authorization-v1.json");
        JsonNode target = template.path("runtimeGovernanceTarget");
        JsonNode execution = template.path("execution");

        assertThat(target.path("table").asText())
                .isEqualTo("workflow_role_canary_scope_governance");
        assertThat(target.path("aggregate").asText()).isEqualTo("CanaryGovernanceRecord");
        assertThat(target.path("keyCount").asInt()).isEqualTo(6);
        assertThat(target.path("matchCount").asInt()).isEqualTo(1);
        assertThat(target.path("expectedCurrentState").asText())
                .isEqualTo("APPROVED_NOT_ENABLED");
        assertThat(template.path("scope")).isEqualTo(authorization.path("approvedScope"));
        assertThat(template.path("evidence")).isEqualTo(authorization.path("evidence"));
        assertThat(template.path("authorization").path("enablementAuthorizationCommit").asText())
                .isEqualTo("3904efd7cd2a35736f46988b48170a7cd2813654");
        for (String field : new String[] {"runtimeReleaseTag", "runtimeReleaseCommit",
                "runtimeTagObject"}) {
            assertThat(template.path("releaseIdentity").path(field).asText()).as(field)
                    .isEqualTo(authorization.path("releaseIdentity").path(field).asText());
        }
        assertThat(WorkflowCanonicalHashSupport.sha256(
                execution.path("operationKeyCanonical").asText()))
                .isEqualTo(execution.path("operationKey").asText());
        assertThat(execution.path("status").asText())
                .isEqualTo("PENDING_RUNTIME_ENABLEMENT_EXECUTION");
        assertThat(execution.path("enableEventCreated").asBoolean()).isFalse();
        assertThat(execution.path("runtimeEnablementExecuted").asBoolean()).isFalse();
    }

    @Test
    void staleAuthorizationScopeEvidenceReleaseAndKillSwitchDriftFailEligibility()
            throws Exception {
        JsonNode template = read("rc2-canary-runtime-enablement-execution-v1.json");
        assertThat(eligible(template, "3904efd7cd2a35736f46988b48170a7cd2813654",
                template.path("scope"), template.path("evidence"),
                template.path("releaseIdentity"), "STOP_NEW_AND_CLAIM")).isTrue();
        assertThat(eligible(template, "0".repeat(40), template.path("scope"),
                template.path("evidence"), template.path("releaseIdentity"),
                "STOP_NEW_AND_CLAIM")).isFalse();
        JsonNode scopeDrift = template.path("scope").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) scopeDrift).put("nodeId", 990405);
        assertThat(eligible(template, "3904efd7cd2a35736f46988b48170a7cd2813654",
                scopeDrift, template.path("evidence"), template.path("releaseIdentity"),
                "STOP_NEW_AND_CLAIM")).isFalse();
        JsonNode evidenceDrift = template.path("evidence").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) evidenceDrift)
                .put("contentHash", "0".repeat(64));
        assertThat(eligible(template, "3904efd7cd2a35736f46988b48170a7cd2813654",
                template.path("scope"), evidenceDrift, template.path("releaseIdentity"),
                "STOP_NEW_AND_CLAIM")).isFalse();
        JsonNode releaseDrift = template.path("releaseIdentity").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) releaseDrift)
                .put("runtimeReleaseCommit", "0".repeat(40));
        assertThat(eligible(template, "3904efd7cd2a35736f46988b48170a7cd2813654",
                template.path("scope"), template.path("evidence"), releaseDrift,
                "STOP_NEW_AND_CLAIM")).isFalse();
        assertThat(eligible(template, "3904efd7cd2a35736f46988b48170a7cd2813654",
                template.path("scope"), template.path("evidence"),
                template.path("releaseIdentity"), "ALLOW")).isFalse();
    }

    @Test
    void duplicateAndConcurrentEnableHaveOneAppendOnlyWinnerAndNeverEnableRoleRuntime()
            throws Exception {
        CasRepository repository = new CasRepository();
        AtomicLong ids = new AtomicLong();
        WorkflowIdentityGenerator generator = ids::incrementAndGet;
        CanaryGovernanceApplicationService service = new CanaryGovernanceApplicationService(
                repository, generator, Clock.fixed(NOW, ZoneOffset.UTC));
        CanaryApprovalEvidence evidence = new CanaryApprovalEvidence("1", 2, HASH, HASH,
                HASH, HASH, "workflow-v1.0.0-rc2.1",
                "c5946d272e8eb88115671d66b46e8c8ec67b1477", HASH);
        service.propose(SCOPE, evidence, "prepared");
        service.transition(SCOPE, 1, CanaryGovernanceState.APPROVED_NOT_ENABLED,
                "human", "approved");

        AtomicInteger successes = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> command = () -> {
                start.await();
                try {
                    service.transition(SCOPE, 2, CanaryGovernanceState.ENABLED,
                            "enablement-gate", "same-operation-key");
                    successes.incrementAndGet();
                } catch (RuntimeException ignored) {
                    // Expected for the duplicate/replay loser.
                }
                return null;
            };
            var one = executor.submit(command);
            var two = executor.submit(command);
            start.countDown();
            one.get();
            two.get();
        }
        assertThat(successes).hasValue(1);
        assertThat(repository.latest(SCOPE, NOW).orElseThrow().state())
                .isEqualTo(CanaryGovernanceState.ENABLED);
        assertThat(repository.appendCount()).isEqualTo(3);
        assertThat(read("rc2-canary-runtime-enablement-execution-v1.json")
                .path("preExecutionState").path("roleRuntimeEnabled").asBoolean()).isFalse();
    }

    private static boolean eligible(JsonNode template, String authorizationCommit,
            JsonNode scope, JsonNode evidence, JsonNode release, String killSwitch) {
        return template.path("authorization").path("enablementAuthorizationCommit").asText()
                        .equals(authorizationCommit)
                && template.path("scope").equals(scope)
                && template.path("evidence").equals(evidence)
                && template.path("releaseIdentity").equals(release)
                && template.path("preExecutionState").path("killSwitch").asText()
                        .equals(killSwitch)
                && !scope.toString().contains("null")
                && template.path("runtimeGovernanceTarget").path("matchCount").asInt() == 1;
    }

    private static JsonNode read(String file) throws Exception {
        return JSON.readTree(Files.readString(FIXTURE.resolve(file)));
    }

    private static final class CasRepository implements CanaryGovernanceRepository {
        private final AtomicReference<CanaryGovernanceRecord> current = new AtomicReference<>();
        private final AtomicInteger appends = new AtomicInteger();

        @Override
        public void insert(CanaryGovernanceRecord record) {
            CanaryGovernanceRecord expected = current.get();
            if ((expected == null && record.previousRecordId() != null)
                    || (expected != null && !Objects.equals(expected.id(),
                            record.previousRecordId()))
                    || !current.compareAndSet(expected, record)) {
                throw new IllegalStateException("concurrent or duplicate Canary governance revision");
            }
            appends.incrementAndGet();
        }

        @Override
        public Optional<CanaryGovernanceRecord> latest(CanaryScope scope, Instant at) {
            CanaryGovernanceRecord value = current.get();
            return value != null && value.scope().equals(scope) ? Optional.of(value)
                    : Optional.empty();
        }

        int appendCount() {
            return appends.get();
        }
    }
}
