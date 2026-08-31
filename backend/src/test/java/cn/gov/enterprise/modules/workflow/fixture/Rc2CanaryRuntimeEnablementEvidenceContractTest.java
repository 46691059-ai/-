package cn.gov.enterprise.modules.workflow.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Rc2CanaryRuntimeEnablementEvidenceContractTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Path ROOT=Rc2ControlledCanaryFixtureContract.REPOSITORY_ROOT
            .resolve("database/test-fixtures/rc2");

    @Test void evidenceFreezesExactSingleEnableAndSafetyBoundaries() throws Exception {
        JsonNode evidence=read("rc2-canary-runtime-enablement-evidence-v1.json");
        JsonNode execution=evidence.path("execution");
        assertThat(evidence.path("schemaVersion").asText())
                .isEqualTo("RC2_CANARY_RUNTIME_ENABLEMENT_EVIDENCE_V1");
        assertThat(evidence.path("scope").path("roleCode").asText())
                .isEqualTo("RC1_TEST_CANARY_APPROVER");
        assertThat(execution.path("enableEventCount").asInt()).isEqualTo(1);
        assertThat(execution.path("enableEventRevision").asInt()).isEqualTo(3);
        assertThat(execution.path("eventSequence")).hasSize(3);
        assertThat(execution.path("runtimeEnablementExecuted").asBoolean()).isTrue();
        assertThat(evidence.path("postExecutionState").path("canaryState").asText())
                .isEqualTo("ENABLED");
        assertThat(evidence.path("postExecutionState").path("roleRuntimeEnabled").asBoolean())
                .isFalse();
        assertThat(evidence.path("postExecutionState").path("killSwitch").asText())
                .isEqualTo("STOP_NEW_AND_CLAIM");
        assertThat(evidence.path("safety").findValuesAsText("scopeWideningOccurred"))
                .containsExactly("false");
    }

    @Test void executionArtifactPreservesOperationKeyAndRecordsCompletedState()
            throws Exception {
        JsonNode artifact=read("rc2-canary-runtime-enablement-execution-v1.json");
        JsonNode execution=artifact.path("execution");
        assertThat(execution.path("operationKey").asText()).hasSize(64);
        assertThat(execution.path("status").asText()).isEqualTo("RUNTIME_CANARY_ENABLED");
        assertThat(execution.path("enableEventCount").asInt()).isEqualTo(1);
        assertThat(execution.path("runtimeEnablementAt").asText())
                .isEqualTo("2026-08-31T08:22:53.611Z");
        assertThat(artifact.path("preExecutionState").path("canaryState").asText())
                .isEqualTo("APPROVED_NOT_ENABLED");
    }

    private static JsonNode read(String name) throws Exception {
        return JSON.readTree(Files.readString(ROOT.resolve(name)));
    }
}
