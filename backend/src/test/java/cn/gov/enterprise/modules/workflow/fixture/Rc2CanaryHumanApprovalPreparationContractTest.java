package cn.gov.enterprise.modules.workflow.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class Rc2CanaryHumanApprovalPreparationContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path FIXTURE = Rc2ControlledCanaryFixtureContract.REPOSITORY_ROOT
            .resolve("database/test-fixtures/rc2");

    @Test
    void recordedHumanApprovalBindsExactReleaseScopeEvidenceAndDirectory() throws Exception {
        JsonNode decision = read("rc2-canary-human-approval-decision-v1.json");
        JsonNode evidence = read("rc2-canary-approval-evidence-v1.json");
        JsonNode attestation = read("rc2-canary-post-tag-release-attestation-v1.json");

        assertThat(decision.path("schemaVersion").asText())
                .isEqualTo("RC2_CANARY_HUMAN_APPROVAL_DECISION_V1");
        assertThat(decision.path("approvalSubject").path("runtimeReleaseTag").asText())
                .isEqualTo(attestation.path("hardenedRelease").path("tag").asText());
        assertThat(decision.path("approvalSubject").path("runtimeReleaseCommit").asText())
                .isEqualTo(attestation.path("hardenedRelease").path("commit").asText());
        assertThat(decision.path("approvalSubject").path("attestationCommit").asText())
                .isEqualTo("d627c38af00eb6be5f2a8fda572679c62c4937c3");
        assertThat(decision.path("approvalSubject").path("preDecisionBaselineCommit").asText())
                .isEqualTo("e96b498deb97c7c2a0d32b9165bbe1edef99b1e0");
        assertThat(decision.path("scope")).isEqualTo(evidence.path("scope"));
        assertThat(decision.path("evidence").path("directoryResultHash").asText())
                .isEqualTo(evidence.path("directoryResultHash").asText());
        assertThat(decision.path("evidence").path("versionBindingHash").asText())
                .isEqualTo(evidence.path("versionBindingHash").asText());
        assertThat(decision.path("evidence").path("manifestHash").asText())
                .isEqualTo(evidence.path("manifestHash").asText());
        assertThat(decision.path("evidence").path("contentHash").asText())
                .isEqualTo(evidence.path("contentHash").asText());
        assertThat(decision.path("evidence").path("structuralFingerprint").asText())
                .isEqualTo(evidence.path("structuralFingerprint").asText());
        assertThat(decision.path("directory").path("revision").asText()).isEqualTo("1");
        assertThat(decision.path("directory").path("candidateCount").asInt()).isEqualTo(2);
        assertThat(decision.path("directory").path("recommendedCandidateCount").asInt())
                .isEqualTo(1);
    }

    @Test
    void explicitHumanDecisionIsRecordedAsApprovedNotEnabled() throws Exception {
        JsonNode root = read("rc2-canary-human-approval-decision-v1.json");
        List<String> requiredTextFields = List.of(
                "/approvalSubject/runtimeReleaseTag", "/approvalSubject/runtimeReleaseCommit",
                "/approvalSubject/attestationCommit",
                "/approvalSubject/preDecisionBaselineCommit", "/scope/roleCode",
                "/evidence/directoryResultHash", "/evidence/versionBindingHash",
                "/evidence/manifestHash", "/evidence/contentHash",
                "/evidence/structuralFingerprint");
        List<String> requiredNumericScopeFields = List.of(
                "/scope/enterpriseId", "/scope/organizationId", "/scope/definitionId",
                "/scope/definitionVersionId", "/scope/nodeId");

        assertThat(isCompleteApprovalArtifact(root, requiredTextFields,
                requiredNumericScopeFields)).isTrue();
        requiredTextFields.forEach(pointer ->
                assertThat(root.at(pointer).isTextual() && !root.at(pointer).asText().isBlank())
                        .as(pointer).isTrue());
        requiredNumericScopeFields.forEach(pointer ->
                assertThat(root.at(pointer).isIntegralNumber()).as(pointer).isTrue());
        for (String pointer : requiredTextFields) {
            JsonNode incomplete = root.deepCopy();
            remove(incomplete, pointer);
            assertThat(isCompleteApprovalArtifact(incomplete, requiredTextFields,
                    requiredNumericScopeFields)).as("missing " + pointer).isFalse();
        }
        for (String pointer : requiredNumericScopeFields) {
            JsonNode incomplete = root.deepCopy();
            remove(incomplete, pointer);
            assertThat(isCompleteApprovalArtifact(incomplete, requiredTextFields,
                    requiredNumericScopeFields)).as("missing " + pointer).isFalse();
        }
        JsonNode recorded = root.path("decision");
        assertThat(recorded.path("previousStatus").asText())
                .isEqualTo("PENDING_HUMAN_APPROVAL");
        assertThat(recorded.path("status").asText()).isEqualTo("APPROVED_NOT_ENABLED");
        assertThat(recorded.path("humanDecision").asText()).isEqualTo("APPROVE");
        assertThat(recorded.path("decisionSource").asText())
                .isEqualTo("EXPLICIT_HUMAN_DECISION");
        assertThat(recorded.path("decisionActorType").asText()).isEqualTo("HUMAN");
        assertThat(recorded.path("decisionActorReference").asText())
                .isEqualTo("EXPLICIT_INTERACTIVE_APPROVER");
        assertThat(recorded.path("decisionBy").isNull()).isTrue();
        assertThat(Instant.parse(recorded.path("decisionAt").asText())).isNotNull();
        assertThat(recorded.path("decisionReason").asText())
                .isEqualTo("EXPLICIT_HUMAN_APPROVAL");
    }

    @Test
    void approvalEventIsAppendOnlyAndDoesNotCreateEnablementEvents() throws Exception {
        JsonNode root = read("rc2-canary-human-approval-decision-v1.json");
        JsonNode governance = root.path("governance");
        assertThat(governance.path("approvalEvent").asText()).isEqualTo("APPROVE");
        assertThat(governance.path("appendOnly").asBoolean()).isTrue();
        assertThat(governance.path("approvalDoesNotEnable").asBoolean()).isTrue();
        assertThat(governance.path("roleRuntimeEnablementSeparate").asBoolean()).isTrue();
        assertThat(governance.path("canaryAuthorized").asBoolean()).isTrue();
        assertThat(governance.path("canaryEnabled").asBoolean()).isFalse();
        assertThat(governance.path("roleRuntimeEnabled").asBoolean()).isFalse();
        assertThat(governance.path("enableEventCreated").asBoolean()).isFalse();
        assertThat(governance.path("roleRuntimeEventCreated").asBoolean()).isFalse();
        assertThat(governance.path("killSwitch").asText())
                .isEqualTo("STOP_NEW_AND_CLAIM");
    }

    private static JsonNode read(String file) throws Exception {
        return JSON.readTree(Files.readString(FIXTURE.resolve(file)));
    }

    private static boolean isCompleteApprovalArtifact(JsonNode root, List<String> textFields,
            List<String> numericFields) {
        return textFields.stream().allMatch(pointer -> root.at(pointer).isTextual()
                        && !root.at(pointer).asText().isBlank())
                && numericFields.stream().allMatch(pointer -> root.at(pointer).isIntegralNumber());
    }

    private static void remove(JsonNode root, String pointer) {
        int separator = pointer.lastIndexOf('/');
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.at(pointer.substring(0, separator)))
                .remove(pointer.substring(separator + 1));
    }
}
