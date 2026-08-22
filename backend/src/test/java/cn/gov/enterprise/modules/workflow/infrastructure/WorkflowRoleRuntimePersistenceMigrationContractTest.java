package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimePersistenceMigrationContractTest {
    private final Path migration = Path.of("../database/migration/mysql/"
            + "V2.6.9__create_role_runtime_snapshot.sql");

    @Test
    void migrationMustCreateOnlyApprovalAndRoleRuntimeSnapshotFacts() throws Exception {
        String sql = Files.readString(migration);
        assertThat(sql).contains(
                "CREATE TABLE role_runtime_binding_approval",
                "CREATE TABLE workflow_role_runtime_binding_snapshot",
                "PENDING", "APPROVED", "REJECTED", "EXPIRED",
                "FROZEN", "ARCHIVED", "SECURITY_BLOCKED",
                "UNIQUE KEY uk_role_runtime_approval_hash",
                "UNIQUE KEY uk_role_runtime_snapshot_node",
                "FOREIGN KEY", "CHECK", "delete_token", "version INT",
                "CHARACTER SET ascii COLLATE ascii_bin");
        assertThat(sql).doesNotContain(
                "CREATE TABLE workflow_task_candidate_pool",
                "CREATE TABLE workflow_task",
                "INSERT INTO workflow_task",
                "INSERT INTO workflow_task_candidate_pool",
                "UPDATE workflow_instance",
                "ROLE_DIRECTORY_V1', 'ACTIVE");
    }

    @Test
    void foundationMustBePromotedOnlyWithTheCompleteForwardFixChain()
            throws Exception {
        String inventory = Files.readString(Path.of("../database/flyway/migration-inventory.yml"));
        assertThat(inventory).contains(
                "V2.6.9__create_role_runtime_snapshot.sql",
                "asset_status: \"CANONICAL_IMMUTABLE\"",
                "execution_status: \"VALIDATED_WITH_V2610_V2611_V2612\"",
                "flyway_checksum: 1523438049",
                "depends_on: [\"2.6.8\"]");
    }

    @Test
    void migrationDdlFingerprintMustMatchGovernedShaAsset() throws Exception {
        byte[] bytes = Files.readAllBytes(migration);
        String fingerprint = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
        String sums = Files.readString(Path.of("../database/migration/mysql/SHA256SUMS"),
                StandardCharsets.UTF_8);
        assertThat(sums).contains(fingerprint + "  " + migration.getFileName());
    }
}
