package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimePersistenceIntegrityMigrationContractTest {
    private final Path migration = Path.of("../database/migration/mysql/"
            + "V2.6.10__harden_role_runtime_persistence_integrity.sql");

    @Test
    void migrationMustGuardDirtyDataBeforePermanentDdl() throws Exception {
        String sql = Files.readString(migration);
        int guard = sql.indexOf("CREATE TEMPORARY TABLE tmp_role_runtime_v2610_guard");
        int approvalAlter = sql.indexOf("ALTER TABLE role_runtime_binding_approval");
        int snapshotAlter = sql.indexOf("ALTER TABLE workflow_role_runtime_binding_snapshot");

        assertThat(guard).isGreaterThanOrEqualTo(0);
        assertThat(approvalAlter).isGreaterThan(guard);
        assertThat(snapshotAlter).isGreaterThan(guard);
        assertThat(sql).contains(
                "version <> 0 OR updated_time <> created_time",
                "approval.status <> 'APPROVED'",
                "CONSTRAINT chk_role_runtime_v2610_guard CHECK (violation_count = 0)");
        assertThat(sql).doesNotContain("UPDATE role_runtime_binding_approval SET",
                "UPDATE workflow_role_runtime_binding_snapshot SET",
                "DELETE FROM role_runtime_binding_approval",
                "DELETE FROM workflow_role_runtime_binding_snapshot");
    }

    @Test
    void migrationMustEnforceAppendOnlyAndApprovedOwnership() throws Exception {
        String sql = Files.readString(migration);
        assertThat(sql).contains(
                "chk_role_runtime_approval_resolver_identity",
                "chk_role_runtime_snapshot_resolver_identity",
                "CHAR_LENGTH(TRIM(resolver_version)) > 0",
                "CREATE TRIGGER trg_role_runtime_approval_no_update",
                "CREATE TRIGGER trg_workflow_role_runtime_snapshot_insert_guard",
                "CREATE TRIGGER trg_workflow_role_runtime_snapshot_no_update",
                "CREATE TRIGGER trg_workflow_role_runtime_snapshot_no_delete",
                "ROLE_RUNTIME_APPROVAL_APPEND_ONLY",
                "ROLE_RUNTIME_APPROVAL_NOT_APPROVED",
                "ROLE_RUNTIME_APPROVAL_CONTRACT_MISMATCH",
                "ROLE_RUNTIME_SNAPSHOT_HASH_INVALID",
                "ROLE_RUNTIME_SNAPSHOT_IMMUTABLE",
                "SIGNAL SQLSTATE '45000'",
                "MYSQL_ERRNO = 1644");
    }

    @Test
    void forwardFixMustBePromotedOnlyWithV2611AndV2612() throws Exception {
        String inventory = Files.readString(Path.of("../database/flyway/migration-inventory.yml"));
        assertThat(inventory).contains(
                "V2.6.10__harden_role_runtime_persistence_integrity.sql",
                "asset_status: \"CANONICAL_IMMUTABLE\"",
                "execution_status: \"VALIDATED_WITH_V2611_V2612\"",
                "flyway_checksum: 936550308",
                "depends_on: [\"2.6.9\"]");

        String resolver = Files.readString(Path.of("src/main/java/cn/gov/enterprise/modules/"
                + "workflow/domain/role/RoleDirectoryResolver.java"));
        assertThat(resolver).contains("ResolverStatus.PREPARED, false");
    }

    @Test
    void migrationMustNotCreateRuntimeTasksPoolsOrBusinessIntegration() throws Exception {
        String sql = Files.readString(migration);
        assertThat(sql).doesNotContain(
                "CREATE TABLE workflow_task",
                "CREATE TABLE workflow_task_candidate_pool",
                "INSERT INTO workflow_task",
                "INSERT INTO workflow_task_candidate_pool",
                "ROLE_DIRECTORY_V1', 'ACTIVE",
                "investment_");
    }
}
