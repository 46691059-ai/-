package cn.gov.enterprise.modules.workflow.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Rc2CanaryObservationEvidenceContractTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Path EVIDENCE=Rc2ControlledCanaryFixtureContract.REPOSITORY_ROOT
            .resolve("database/test-fixtures/rc2/rc2-canary-observation-evidence-v1.json");

    @Test void identityRuntimeStateIsolationAndResultAreFrozen() throws Exception {
        JsonNode root=read();
        JsonNode identity=root.path("identity");
        assertThat(identity.path("observationBaselineCommit").asText())
                .isEqualTo("fd833003e6134b7c0e39a08d513fd0aa2af195eb");
        assertThat(identity.path("runtimeEnablementEvidenceCommit").asText())
                .isEqualTo("fd833003e6134b7c0e39a08d513fd0aa2af195eb");
        assertThat(identity.path("runtimeGovernanceBootstrapCommit").asText())
                .isEqualTo("111297fe0174525549e11ba66e5005667f15890e");
        assertThat(identity.path("enablementAuthorizationCommit").asText())
                .isEqualTo("3904efd7cd2a35736f46988b48170a7cd2813654");
        JsonNode runtime=root.path("runtimeState");
        assertThat(runtime.path("canaryEnabled").asBoolean()).isTrue();
        assertThat(runtime.path("enableEventCount").asInt()).isEqualTo(1);
        assertThat(runtime.path("enableEventRevision").asInt()).isEqualTo(3);
        assertThat(runtime.path("roleRuntimeEnabled").asBoolean()).isFalse();
        assertThat(runtime.path("killSwitch").asText()).isEqualTo("STOP_NEW_AND_CLAIM");
        assertThat(root.path("businessIsolation").path("trafficReleased").asBoolean())
                .isFalse();
        assertThat(root.path("result").path("observationResult").asText())
                .isEqualTo("PASS");
    }

    @Test void telemetryIsExplicitlyPartialAndNotEncodedAsNumericZero() throws Exception {
        JsonNode telemetry=read().path("telemetry");
        assertThat(telemetry.path("completeness").asText()).isEqualTo("PARTIAL");
        for(String field:new String[]{"businessErrorTelemetry","businessLatencyTelemetry"}){
            assertThat(telemetry.path(field).isTextual()).as(field).isTrue();
            assertThat(telemetry.path(field).asText()).as(field).isEqualTo("NOT_OBSERVED");
        }
    }

    @Test void exactScopeWindowAndStableSnapshotContractAreFrozen() throws Exception {
        JsonNode root=read();
        assertThat(root.path("scope").toString()).doesNotContain("null","*");
        assertThat(root.path("observationWindow").path("snapshotCount").asInt())
                .isEqualTo(2);
        assertThat(root.path("observationWindow").path("durationMillis").asLong())
                .isEqualTo(10064L);
        assertThat(root.path("result").path("observationStateStable").asBoolean()).isTrue();
    }

    private static JsonNode read() throws Exception {
        return JSON.readTree(Files.readString(EVIDENCE));
    }
}
