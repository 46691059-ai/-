package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class WorkflowTaskClaimMigrationContractTest {
    @Test
    void migrationMustCreateClaimAuditAndConcurrencyConstraints() throws Exception {
        String sql = Files.readString(Path.of("..", "database", "migration", "mysql",
                "V2.6.7__create_workflow_task_claim.sql"));
        assertThat(sql).contains(
                "CREATE TABLE workflow_task_claim (",
                "CREATE TABLE workflow_task_claim_audit (",
                "uk_workflow_task_claim_active",
                "uk_workflow_task_claim_idempotency",
                "fk_workflow_task_claim_pool",
                "fk_workflow_task_claim_member",
                "status = 'PENDING' AND assignee_user_id IS NULL",
                "status IN ('CLAIMED', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')",
                "CHARACTER SET ascii COLLATE ascii_bin",
                "delete_token", "version");
    }

    @Test
    void migrationMustNotImplementReleaseTransferOrResolvers() throws Exception {
        String sql = Files.readString(Path.of("..", "database", "migration", "mysql",
                "V2.6.7__create_workflow_task_claim.sql"));
        assertThat(sql).doesNotContain("CREATE TABLE workflow_task_release",
                "CREATE TABLE workflow_task_transfer", "CREATE TABLE workflow_role_resolver");
        assertThat(sql).doesNotContain("CREATE TEMPORARY TABLE");
    }

    @Test
    void assetMustRemainFrozenAndRequireTheV268ForwardFix() throws Exception {
        Path root = Path.of("..", "database");
        String inventory = Files.readString(root.resolve("flyway/migration-inventory.yml"));
        String canonical = inventory.substring(inventory.indexOf("canonical_assets:"));
        assertThat(canonical).contains("version: \"2.6.7\"",
                "asset_status: \"CANONICAL_IMMUTABLE\"",
                "execution_status: \"EPHEMERAL_MYSQL8_VALIDATED_WITH_V268\"",
                "flyway_checksum: 1689998435");
        assertThat(inventory).contains(
                "FAIL_BLOCKING_CLAIM_OWNERSHIP_AND_AUDIT_INTEGRITY_CONSTRAINTS",
                "V2.6.7 is deployable only when immediately followed by V2.6.8");
        byte[] bytes = Files.readAllBytes(root.resolve(
                "migration/mysql/V2.6.7__create_workflow_task_claim.sql"));
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThat(actual).isEqualTo(
                "800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed");
    }
}
