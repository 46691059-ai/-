package cn.gov.enterprise.modules.workflow.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Rc2ControlledCanaryFixtureContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path FIXTURE = ROOT.resolve("database/test-fixtures/rc2");
    private static JsonNode manifest;

    @BeforeAll
    static void loadManifest() throws Exception {
        manifest = new ObjectMapper().readTree(Files.readString(
                FIXTURE.resolve("rc2-canary-fixture-manifest.json")));
    }

    @Test
    void fixtureIdentityAndReservedIdsAreExplicitAndUnique() {
        assertThat(manifest.path("fixtureContract").asText())
                .isEqualTo("RC2_CONTROLLED_CANARY_FIXTURE_V1");
        assertThat(manifest.path("classification").toString())
                .contains("TEST_ONLY", "NON_PRODUCTION", "DETERMINISTIC", "FAIL_CLOSED");
        JsonNode scope = manifest.path("scope");
        List<Long> ids = List.of(scope.path("enterpriseId").asLong(),
                scope.path("organizationId").asLong(), scope.path("candidateUserIds").get(0).asLong(),
                scope.path("candidateUserIds").get(1).asLong(), scope.path("approvalRoleId").asLong(),
                scope.path("revisionHeadId").asLong(), scope.path("assignmentIds").get(0).asLong(),
                scope.path("assignmentIds").get(1).asLong(), scope.path("revisionId").asLong(),
                scope.path("definitionId").asLong(), scope.path("definitionVersionId").asLong(),
                scope.path("roleNodeId").asLong(), scope.path("versionBindingId").asLong(),
                scope.path("bindingManifestId").asLong(), scope.path("versionReleaseId").asLong());
        assertThat(new HashSet<>(ids)).hasSameSizeAs(ids);
        assertThat(ids).allMatch(id -> id >= 990000 && id < 1000000);
        assertThat(scope.path("roleCode").asText()).startsWith("RC1_TEST_CANARY_");
        assertThat(scope.path("definitionCode").asText()).startsWith("RC2_TEST_");
    }

    @Test
    void guardFreezesRc2SchemaAndRejectsRc1AndProductionTargets() throws Exception {
        String guard = text("00_rc2_canary_guard.ps1");
        String collector = text("collect-rc2-environment-evidence.ps1");
        String verifier = text("verify-rc2-environment-attestation.ps1");
        assertThat(guard).contains("DEPRECATED", "verify-rc2-environment-attestation.ps1",
                "OLD_GUARD_USED_AS_SECOND_AUTHORITY=NO");
        assertThat(collector).contains("--default-character-set=utf8mb4",
                "migrationSnapshotSourceCount", "schemaStructuralFingerprint",
                "schemaMetadataFingerprint", "Copy-Item -LiteralPath $source");
        assertThat(verifier).contains("2.6.23", "34061", "D:\\mysql-rc1\\data",
                "EPHEMERAL_RC2_TEST_IDENTITY_V1", "STOP_NEW_AND_CLAIM",
                "failedAssertions", "RC2_ENVIRONMENT_ATTESTATION_CANONICAL_V2",
                "rc2-schema-fingerprint-baseline.json");
        JsonNode fingerprintBaseline = new ObjectMapper().readTree(Files.readString(
                FIXTURE.resolve("rc2-schema-fingerprint-baseline.json")));
        assertThat(fingerprintBaseline.path("canonicalVersion").asText())
                .isEqualTo("RC2_SCHEMA_STRUCTURAL_CANONICAL_V2");
        assertThat(fingerprintBaseline.path("structuralFingerprint").asText())
                .isEqualTo("92ea9f4d006233f672b22c3c335663c04121dbb652ce657aac07d7b04518171d");
        assertThat(verifier).doesNotContain("mysql.exe", "Get-NetTCPConnection",
                "information_schema", "Get-FileHash");
        assertThat(guard).doesNotMatch(Pattern.compile(
                "(?im)^\\s*param\\s*\\([^)]*(?:Force|skipGuard|ignoreFingerprint)"));
    }

    @Test
    void guardPolicyMatrixCoversEveryFailClosedBoundary() throws Exception {
        String tests = text("test-rc2-environment-attestation.ps1");
        assertThat(tests).contains(
                "VALID_EVIDENCE", "RC1_PORT", "RC1_DATADIR", "WRONG_PORT",
                "STRUCTURAL_FINGERPRINT_MISMATCH", "STRUCTURAL_COLUMN_REMOVED",
                "STRUCTURAL_FK_REMOVED", "STRUCTURAL_TRIGGER_REMOVED",
                "STRUCTURAL_CHECK_CHANGED", "WRONG_MIGRATION",
                "MIGRATION_SHA_MISMATCH", "RUNTIME_ENABLED", "CANARY_AUTHORIZED",
                "UNSAFE_KILL_SWITCH", "PARTIAL_FIXTURE", "FIXTURE_ID_CONFLICT",
                "MARKER_INVALID", "PROVIDER_ENABLED", "SNAPSHOT_COUNT_ZERO",
                "WILDCARD_LITERAL_COPY_REGRESSION", "ISO_STRING_PS51", "ISO_STRING_PS7",
                "FAILED_ASSERTIONS_COMPLETE_SET");
        JsonNode marker = new ObjectMapper().readTree(Files.readString(
                FIXTURE.resolve("rc2-test-environment-marker.template.json")));
        assertThat(marker.path("contract").asText())
                .isEqualTo("EPHEMERAL_RC2_TEST_IDENTITY_V1");
        assertThat(marker.path("environment").asText()).isEqualTo("RC2_TEST");
        assertThat(marker.path("createdByValidationScript").asBoolean()).isTrue();
        assertThat(marker.path("roleRuntime").asText()).isEqualTo("DISABLED");
        assertThat(marker.path("canary").asText()).isEqualTo("NOT_AUTHORIZED_NOT_ENABLED");
        assertThat(marker.path("killSwitch").asText()).isEqualTo("STOP_NEW_AND_CLAIM");
    }

    @Test
    void powershellFiveAndSevenReadEffectiveAtAsTheRawCanonicalJsonString()
            throws Exception {
        String collector = text("collect-rc2-environment-evidence.ps1");
        String fixtureVerifier = text("verify-rc2-fixture-contract.ps1");
        String publish = text("04_rc2_canary_publish.ps1");
        assertThat(collector).contains("Get-RawJsonString $manifestRaw 'directoryEffectiveAt'",
                "System.Web.Script.Serialization.JavaScriptSerializer");
        assertThat(fixtureVerifier).contains("Get-RawJsonString $raw 'directoryEffectiveAt'");
        assertThat(publish).contains("$fixtureManifestText", "$directoryEffectiveAtMatch")
                .doesNotContain("[string]$fixtureManifest.effectiveWindow.directoryEffectiveAt");
    }

    @Test
    void assetsContainNoUpsertRuntimeDmlOrSecretMaterial() throws Exception {
        String identity = text("01_rc2_canary_identity.sql");
        String publish = text("04_rc2_canary_publish.ps1");
        String decommission = text("99_rc2_canary_decommission-plan.sql");
        String writable = identity + publish + decommission;
        assertThat(writable).doesNotMatch(Pattern.compile(
                "(?im)^\\s*(INSERT\\s+IGNORE|REPLACE\\s+INTO)\\b"));
        assertThat(writable).doesNotMatch(Pattern.compile(
                "(?i)ON\\s+DUPLICATE\\s+KEY\\s+UPDATE"));
        assertThat(writable).doesNotMatch(Pattern.compile(
                "(?i)(?:DB_PASSWORD|JWT_SECRET)\\s*=\\s*['\"][^'\"]+['\"]"));
        assertThat(writable).doesNotContain("service-token:",
                "workflow_instance ", "workflow_task ", "workflow_task_candidate_pool ",
                "workflow_task_candidate_member ", "workflow_task_claim ",
                "workflow_role_runtime_execution_admission ",
                "workflow_role_realtime_eligibility_");
        assertThat(identity).contains("INSERT INTO sys_org", "INSERT INTO sys_user",
                "!RC2_TEST_NO_LOGIN_HASH!", "locked_until");
        assertThat(identity).doesNotContain("@", "+86", "SHA2(", "REPEAT(");
    }

    @Test
    void hashesAndPublicationUseTrustedJavaContracts() throws Exception {
        String publish = text("04_rc2_canary_publish.ps1");
        String seeder = Files.readString(ROOT.resolve(
                "backend/src/test/java/cn/gov/enterprise/modules/workflow/fixture/"
                        + "Rc2ControlledCanaryFixtureSeederTest.java"));
        assertThat(publish).contains("Rc2ControlledCanaryFixtureSeederTest",
                "ApprovalRoleCanonical/ApprovalRoleCommandService",
                "VersionNodeResolverBindingCanonical", "ResolverBindingManifestCanonical",
                "PLANNED_STAGES=ENVIRONMENT>TEST_ORGANIZATION", "FIXTURE_DRY_RUN=PASS",
                "DIRECTORY_HASH_PLAN=REVISION_HEAD_AND_DISCOVERY_RESULT_USE_"
                        + "AUTHORITATIVE_FIXTURE_EFFECTIVE_AT",
                "DIRECTORY_EFFECTIVE_AT_MATCH=YES");
        assertThat(seeder).contains("ApprovalRoleCanonical.sha256",
                "ApprovalRoleCommandService", "VersionNodeResolverBindingCanonical.compute",
                "definitions.publishVersion", "resolverRegistry.requireDescriptor",
                "isEqualTo(ApprovalRoleCanonical.resultHash(directoryResult))",
                "Rc2ControlledCanaryFixtureContract.DIRECTORY_EFFECTIVE_AT",
                "isEqualTo(directoryResult.resultHash())");
        assertThat(seeder).contains("RC2_ENVIRONMENT_ATTESTATION_ID",
                "RC2_ENVIRONMENT_ATTESTATION_RESULT_PATH",
                "Environment Attestation evidence mismatch");
        assertThat(seeder).doesNotContain("ROLE_DIRECTORY_V1\", \"ACTIVE",
                "Runtime Binding", "CandidatePool", "ClaimRuntime");
    }

    @Test
    void topologyIsOneRealApprovalNodeWithoutFakeStartOrEnd() throws Exception {
        assertThat(manifest.path("workflow").path("topology").asText())
                .isEqualTo("SINGLE_APPROVAL_NODE");
        assertThat(manifest.path("workflow").path("engineMode").asText())
                .isEqualTo("SINGLE_NODE_LEGACY");
        String seeder = Files.readString(ROOT.resolve(
                "backend/src/test/java/cn/gov/enterprise/modules/workflow/fixture/"
                        + "Rc2ControlledCanaryFixtureSeederTest.java"));
        assertThat(seeder).contains("WorkflowNode.NodeType.APPROVAL",
                "WorkflowNode.ApprovalMode.SINGLE");
        assertThat(seeder).doesNotContain("NodeType.START", "NodeType.END");
    }

    @Test
    void verificationIsReadOnlyAndRequiresZeroRuntimeObjects() throws Exception {
        String verify = text("05_rc2_canary_verify.sql");
        assertThat(verify).startsWith("-- Read-only").contains(
                "ROLE_BOUND_NODE_COUNT", "RUNTIME_INSTANCE_COUNT", "RUNTIME_TASK_COUNT",
                "CANDIDATE_POOL_COUNT", "CLAIM_COUNT", "ADMISSION_COUNT");
        assertThat(verify).doesNotMatch(Pattern.compile(
                "(?im)^\\s*(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|ALTER|DROP|CREATE)\\b"));
    }

    @Test
    void decommissionPreservesAppendOnlyEvidence() throws Exception {
        String plan = text("99_rc2_canary_decommission-plan.sql");
        String seeder = Files.readString(ROOT.resolve(
                "backend/src/test/java/cn/gov/enterprise/modules/workflow/fixture/"
                        + "Rc2ControlledCanaryFixtureSeederTest.java"));
        assertThat(plan).contains("GOVERNED_STATE_TRANSITION", "ApprovalRoleCommandService.endAssignment",
                "ApprovalRoleCommandService.deactivateRole", "Never DELETE");
        assertThat(seeder).contains("approvalRoles.endAssignment", "approvalRoles.deactivateRole",
                "status='RETIRED'", "status='ARCHIVED'");
        assertThat(seeder).doesNotContain("DELETE FROM", "TRUNCATE TABLE");
    }

    @Test
    void fixtureDoesNotAuthorizeCanaryAndPostRc2CandidateSeedsNoScope() throws Exception {
        assertThat(manifest.path("requiredMigrationShaCount").asInt()).isEqualTo(45);
        assertThat(manifest.path("runtimeSafety").path("roleRuntime").asText()).isEqualTo("DISABLED");
        assertThat(manifest.path("runtimeSafety").path("canary").asText())
                .isEqualTo("NOT_AUTHORIZED_NOT_ENABLED");
        String candidate=Files.readString(ROOT.resolve(
                "database/migration/mysql/V2.6.24__create_exact_canary_scope_governance.sql"));
        assertThat(candidate).doesNotContain("INSERT INTO workflow_role_canary_scope_governance");
    }

    private static String text(String name) throws Exception {
        return Files.readString(FIXTURE.resolve(name));
    }
}
