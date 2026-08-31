package cn.gov.enterprise.modules.workflow.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class Rc2CanaryEnablementPreparationContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path FIXTURE = Rc2ControlledCanaryFixtureContract.REPOSITORY_ROOT
            .resolve("database/test-fixtures/rc2");

    @Test
    void preparationBindsApprovalReleaseEvidenceAndExactBlastRadius() throws Exception {
        JsonNode preparation = read("rc2-canary-enablement-preparation-v1.json");
        JsonNode approval = read("rc2-canary-human-approval-decision-v1.json");

        assertThat(preparation.path("schemaVersion").asText())
                .isEqualTo("RC2_CANARY_ENABLEMENT_PREPARATION_V1");
        assertThat(preparation.path("humanApprovalRecordCommit").asText())
                .isEqualTo("e986791bc9a2870916b851e03b07186ebac4449e");
        assertThat(preparation.path("approval").path("state").asText())
                .isEqualTo("APPROVED_NOT_ENABLED");
        assertThat(preparation.path("scope")).isEqualTo(approval.path("scope"));
        for (String field : List.of("directoryResultHash", "versionBindingHash", "manifestHash",
                "contentHash", "structuralFingerprint")) {
            assertThat(preparation.path("evidence").path(field).asText())
                    .as(field).isEqualTo(approval.path("evidence").path(field).asText());
        }
        JsonNode radius = preparation.path("blastRadius");
        assertThat(radius.path("scopeCount").asInt()).isEqualTo(1);
        assertThat(radius.path("directoryCandidateCount").asInt()).isEqualTo(2);
        assertThat(radius.path("maximumRecommendedCandidates").asInt()).isEqualTo(1);
        assertThat(radius.path("currentRuntimeObjectCount").asInt()).isZero();
        assertThat(radius.path("wildcardAllowed").asBoolean()).isFalse();
        assertThat(radius.path("parentScopeInheritanceAllowed").asBoolean()).isFalse();
        assertThat(radius.path("crossScopeFallbackAllowed").asBoolean()).isFalse();
    }

    @Test
    void explicitHumanDecisionAuthorizesButDoesNotExecuteEnablement() throws Exception {
        JsonNode preparation = read("rc2-canary-enablement-preparation-v1.json");
        JsonNode authorization = read("rc2-canary-enablement-authorization-v1.json");
        JsonNode decision = preparation.path("enablementDecision");
        JsonNode controls = preparation.path("runtimeControls");

        assertThat(decision.path("previousStatus").asText())
                .isEqualTo("PENDING_EXPLICIT_HUMAN_ENABLEMENT_DECISION");
        assertThat(decision.path("status").asText()).isEqualTo("AUTHORIZED_TO_ENABLE");
        assertThat(decision.path("enablementDecision").asText()).isEqualTo("ENABLE");
        assertThat(decision.path("decisionSource").asText())
                .isEqualTo("EXPLICIT_HUMAN_ENABLEMENT_DECISION");
        assertThat(decision.path("decisionActorType").asText()).isEqualTo("HUMAN");
        assertThat(decision.path("decisionActorReference").asText())
                .isEqualTo("EXPLICIT_INTERACTIVE_ENABLEMENT_APPROVER");
        assertThat(decision.path("decisionBy").isNull()).isTrue();
        assertThat(Instant.parse(decision.path("decisionAt").asText())).isNotNull();
        assertThat(controls.path("canaryEnabled").asBoolean()).isFalse();
        assertThat(controls.path("enableEventCreated").asBoolean()).isFalse();
        assertThat(controls.path("roleRuntimeEnabled").asBoolean()).isFalse();
        assertThat(controls.path("roleRuntimeActivationEventCreated").asBoolean()).isFalse();
        assertThat(controls.path("approvalDoesNotEnable").asBoolean()).isTrue();
        assertThat(controls.path("roleRuntimeEnablementSeparate").asBoolean()).isTrue();
        assertThat(authorization.path("humanDecision").path("authorizationState").asText())
                .isEqualTo("AUTHORIZED_TO_ENABLE");
        assertThat(authorization.path("runtimeState").path("approvalState").asText())
                .isEqualTo("APPROVED_NOT_ENABLED");
        assertThat(authorization.path("execution").path("runtimeEnablementExecuted").asBoolean())
                .isFalse();
        assertThat(authorization.path("execution").path("runtimeScopeObjectCreated").asBoolean())
                .isFalse();
    }

    @Test
    void killSwitchAndAppendOnlySuspendRollbackContractAreFrozen() throws Exception {
        JsonNode preparation = read("rc2-canary-enablement-preparation-v1.json");
        JsonNode authorization = read("rc2-canary-enablement-authorization-v1.json");
        JsonNode controls = preparation.path("runtimeControls");
        JsonNode rollback = preparation.path("rollback");

        assertThat(controls.path("killSwitch").asText()).isEqualTo("STOP_NEW_AND_CLAIM");
        assertThat(controls.path("killSwitchMustBeRevalidatedAtEnablement").asBoolean()).isTrue();
        assertThat(rollback.path("primaryTransition").asText())
                .isEqualTo("ENABLED_TO_SUSPENDED");
        assertThat(rollback.path("terminalTransition").asText())
                .isEqualTo("ANY_NON_REVOKED_TO_REVOKED");
        assertThat(rollback.path("firstAction").asText())
                .isEqualTo("RESTORE_OR_RETAIN_STOP_NEW_AND_CLAIM");
        assertThat(rollback.path("disableRoleRuntimeSeparately").asBoolean()).isTrue();
        assertThat(rollback.path("blockNewRuntimeAndClaims").asBoolean()).isTrue();
        assertThat(rollback.path("preserveAppendOnlyEvidence").asBoolean()).isTrue();
        assertThat(authorization.path("releaseIdentity").path("preEnableBaselineCommit").asText())
                .isEqualTo("5d6881d509a76be9c8a6874117539bd1e99400b5");
        assertThat(authorization.path("releaseIdentity").path("runtimeReleaseTag").asText())
                .isEqualTo(preparation.path("runtimeRelease").path("tag").asText());
        assertThat(authorization.path("releaseIdentity").path("runtimeReleaseCommit").asText())
                .isEqualTo(preparation.path("runtimeRelease").path("commit").asText());
        assertThat(authorization.path("releaseIdentity").path("runtimeTagObject").asText())
                .isEqualTo(preparation.path("runtimeRelease").path("tagObject").asText());
        assertThat(authorization.path("approvedScope")).isEqualTo(preparation.path("scope"));
        assertThat(authorization.path("evidence")).isEqualTo(preparation.path("evidence"));
        assertThat(authorization.path("runtimeState").path("killSwitch").asText())
                .isEqualTo("STOP_NEW_AND_CLAIM");
        assertThat(authorization.path("execution").path("appendOnlyAuthorizationRecord")
                .asBoolean()).isTrue();
    }

    private static JsonNode read(String file) throws Exception {
        return JSON.readTree(Files.readString(FIXTURE.resolve(file)));
    }
}
