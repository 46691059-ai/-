package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class V2621VersionResolverBindingMigrationContractTest {
    private static final Path MIGRATION_ROOT = Path.of("..", "database", "migration", "mysql");
    private static final Path SQL = MIGRATION_ROOT.resolve(
            "V2.6.21__create_workflow_version_resolver_binding.sql");

    @Test
    void createsExactlyOneV2621CandidateWithSeparatedScope() throws Exception {
        assertThat(SQL).exists();
        try (var files = Files.list(MIGRATION_ROOT)) {
            assertThat(files.filter(path -> path.getFileName().toString().startsWith("V2.6.21__")))
                    .hasSize(1);
        }
        String sql = Files.readString(SQL);
        assertThat(occurrences(sql, "CREATE TABLE ")).isEqualTo(2);
        assertThat(occurrences(sql, "ALTER TABLE ")).isEqualTo(2);
        assertThat(sql).contains(
                "CREATE TABLE workflow_version_node_resolver_binding",
                "CREATE TABLE workflow_version_resolver_binding_manifest",
                "ALTER TABLE workflow_version",
                "ALTER TABLE workflow_version_release");
        assertThat(sql).doesNotContain(
                "ALTER TABLE workflow_instance_resolver_binding",
                "ALTER TABLE workflow_node_resolver_binding_snapshot",
                "workflow_role_realtime_eligibility_capability_evidence",
                "workflow_role_realtime_eligibility_validator_evidence",
                "workflow_role_realtime_eligibility_event",
                "workflow_task_candidate_pool",
                "workflow_task_claim",
                "ALTER TABLE workflow_node");
    }

    @Test
    void freezesLegacyDefaultsAndVersionReleaseSnapshots() throws Exception {
        String sql = Files.readString(SQL);
        assertThat(occurrences(sql, "ADD COLUMN resolver_binding_model")).isEqualTo(2);
        assertThat(occurrences(sql, "ADD COLUMN resolver_binding_manifest_hash")).isEqualTo(2);
        assertThat(occurrences(sql, "ADD COLUMN resolver_binding_count")).isEqualTo(2);
        assertThat(occurrences(sql, "ADD COLUMN resolver_binding_canonical_version")).isEqualTo(2);
        assertThat(sql).contains(
                "NOT NULL DEFAULT 'LEGACY_USER_ONLY'",
                "LEGACY_USER_ONLY",
                "VERSION_RESOLVER_BINDING_CAPABLE",
                "resolver_binding_manifest_hash IS NULL",
                "resolver_binding_count = 0",
                "status IN ('PUBLISHED', 'RETIRED')",
                "VERSION_RESOLVER_BINDING_MANIFEST_V1");
        assertThat(sql).doesNotContain("UPDATE workflow_version", "UPDATE workflow_version_release");
    }

    @Test
    void enforcesOwnedRoleBindingAndStableScopeContract() throws Exception {
        String sql = Files.readString(SQL);
        assertThat(sql).contains(
                "FOREIGN KEY (definition_id, definition_version_id)",
                "REFERENCES workflow_version(definition_id, id)",
                "FOREIGN KEY (definition_version_id, node_id)",
                "REFERENCES workflow_node(version_id, id)",
                "definition_version_id, node_id, binding_order, delete_token",
                "binding_order >= 1",
                "resolver_code = 'ROLE_DIRECTORY'",
                "resolver_version = 'ROLE_DIRECTORY_V1'",
                "strategy_type = 'ROLE'",
                "resolver_mode = 'CANDIDATE_POOL'",
                "target_type = 'ROLE'",
                "REGEXP_LIKE(role_code, '^[A-Z][A-Z0-9_]{2,99}$', 'c')",
                "organization_scope_type = 'FIXED_ORG'",
                "organization_scope_type = 'INSTANCE_BUSINESS_ORG'",
                "effective_time_policy = 'NODE_ACTIVATED_AT'",
                "binding_schema_version = 'VERSION_NODE_RESOLVER_BINDING_V1'",
                "CHARACTER SET ascii COLLATE ascii_bin");
        assertThat(sql).doesNotContain("REFERENCES approval_role");
    }

    @Test
    void definesDraftOnlyBindingAndAppendOnlyReleaseGuards() throws Exception {
        String sql = Files.readString(SQL);
        assertThat(occurrences(sql, "CREATE TRIGGER ")).isEqualTo(9);
        assertThat(sql).contains(
                "trg_workflow_version_node_binding_insert_guard",
                "trg_workflow_version_node_binding_update_guard",
                "trg_workflow_version_node_binding_delete_guard",
                "owner_status <> 'DRAFT'",
                "WORKFLOW_VERSION_BINDING_REQUIRES_DRAFT_VERSION",
                "WORKFLOW_VERSION_BINDING_IMMUTABLE",
                "trg_workflow_version_binding_manifest_insert_guard",
                "trg_workflow_version_binding_manifest_no_update",
                "trg_workflow_version_binding_manifest_no_delete",
                "WORKFLOW_VERSION_BINDING_MANIFEST_APPEND_ONLY",
                "trg_workflow_version_release_binding_insert_guard",
                "WORKFLOW_VERSION_RELEASE_BINDING_SNAPSHOT_MISMATCH",
                "WORKFLOW_VERSION_RELEASE_MANIFEST_MISSING",
                "trg_workflow_version_release_no_update",
                "trg_workflow_version_release_no_delete",
                "WORKFLOW_VERSION_RELEASE_APPEND_ONLY");
    }

    @Test
    void inventoryRecordsCanonicalRealMysqlValidationEvidence() throws Exception {
        String inventory = Files.readString(Path.of("..", "database", "flyway", "migration-inventory.yml"));
        assertThat(inventory).contains(
                "version: \"2.6.21\"",
                "V2.6.21__create_workflow_version_resolver_binding.sql",
                "asset_status: \"CANONICAL_IMMUTABLE\"",
                "execution_status: \"EPHEMERAL_MYSQL8_VALIDATED\"",
                "flyway_checksum: -698735620",
                "schema_fingerprint_sha256: \"25c12ff7e7d50a4c44f7c261b92e29817914a324262b57c225294cefa62fd4ff\"",
                "report: \"docs/workflow-v2621-real-mysql-validation-report.md\"");
    }

    @Test
    void governedMigrationHashesMatchWithoutHistoricalDrift() throws Exception {
        var lines = Files.readAllLines(MIGRATION_ROOT.resolve("SHA256SUMS"), StandardCharsets.UTF_8);
        assertThat(lines).anyMatch(line -> line.endsWith(
                "  V2.6.21__create_workflow_version_resolver_binding.sql"));
        assertThat(lines).anyMatch(line -> line.equals(
                "160f0b649ac85cc177d82c2ff0aa5070639b29c7f2dda72b02438041bd063201"
                        + "  V2.6.20__create_approval_role_directory_provider_audit.sql"));
        for (String line : lines) {
            if (line.isBlank()) continue;
            String[] parts = line.split("  ", 2);
            assertThat(parts).as("governed SHA line: %s", line).hasSize(2);
            Path migration = MIGRATION_ROOT.resolve(parts[1]);
            assertThat(migration).exists();
            assertThat(sha256(migration)).as(parts[1]).isEqualTo(parts[0]);
        }
    }

    private static int occurrences(String source, String target) {
        return (source.length() - source.replace(target, "").length()) / target.length();
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
