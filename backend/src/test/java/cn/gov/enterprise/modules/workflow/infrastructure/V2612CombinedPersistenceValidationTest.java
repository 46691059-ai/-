package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V2612CombinedPersistenceValidationTest {
    private final Path script = Path.of("../database/flyway/scripts/validate-v2612-combined.ps1");

    @Test
    void validationMustCoverFourIsolatedPathsAndStrictFlywayLifecycle() throws Exception {
        String value = Files.readString(script);
        assertThat(value).contains(
                "New-IsolatedMysql 'fresh'",
                "New-IsolatedMysql 'upgrade'",
                "New-IsolatedMysql 'forward'",
                "New-IsolatedMysql 'failed-guard-recovery'",
                "migrate '2.6.12'",
                "Invoke-Flyway $fresh $full validate",
                "Invoke-Flyway $upgrade $full validate",
                "Invoke-Flyway $forward $full validate",
                "No migration necessary");
    }

    @Test
    void validationMustInspectCollationGuardHashTriggersCompatibilityAndConcurrency()
            throws Exception {
        String value = Files.readString(script);
        assertThat(value).contains(
                "character_set_name='ascii'",
                "collation_name='ascii_bin'",
                "role_directory_v1",
                "Role_Directory_V1",
                "ROLE_directory_V1",
                "approval_update",
                "approval_delete",
                "snapshot_update",
                "snapshot_delete",
                "Get-CanonicalHash",
                "workflow_task_candidate_pool",
                "workflow_task_claim",
                "Start-Job",
                "ERROR 1213|ERROR 1205");
    }

    @Test
    void validationMustProtectHistoricalHashesAndNeverEnableRoleRuntime() throws Exception {
        String value = Files.readString(script);
        assertThat(value).contains(
                "V2.6.9__create_role_runtime_snapshot.sql",
                "V2.6.10__harden_role_runtime_persistence_integrity.sql",
                "V2.6.11__repair_role_runtime_historical_integrity_guard.sql",
                "V2.6.12__fix_role_runtime_resolver_version_collation.sql",
                "SHA drift",
                "Migration SHA list mismatch");
        assertThat(value).doesNotContain(
                "V2.6.13",
                "ROLE_DIRECTORY_V1', 'ACTIVE",
                "INSERT INTO workflow_task_candidate_pool",
                "INSERT INTO workflow_task_claim",
                "INSERT INTO investment_",
                "UPDATE investment_",
                "DELETE FROM investment_");
    }
}
