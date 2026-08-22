package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V2611CombinedValidationContractTest {
    private final Path script = Path.of("../database/flyway/scripts/validate-v2611-combined.ps1");

    @Test
    void scriptMustUseThreeIsolatedMigrationPaths() throws Exception {
        String value = Files.readString(script);
        assertThat(value).contains(
                "New-IsolatedMysql 'fresh'",
                "New-IsolatedMysql 'upgrade'",
                "New-IsolatedMysql 'forward'",
                "migrate '2.6.8'",
                "migrate '2.6.9'",
                "migrate '2.6.10'",
                "migrate '2.6.11'",
                "No migration necessary",
                "Fingerprint mismatch");
    }

    @Test
    void scriptMustExerciseDirtyGuardsBeforeV2611PermanentObjects() throws Exception {
        String value = Files.readString(script);
        assertThat(value).contains(
                "guard-resolver",
                "guard-contract",
                "guard-hash",
                "guard-canonical",
                "illegal_resolver_version",
                "approval_snapshot_contract_mismatch",
                "invalid_binding_hash_matrix",
                "canonical_hash_drift",
                "Get-V2611PermanentCount",
                "chk_role_runtime_v2611_guard|3819");
    }

    @Test
    void scriptMustValidateTriggersCompatibilityAndSingleWinner() throws Exception {
        String value = Files.readString(script);
        assertThat(value).contains(
                "approval_update",
                "approval_delete",
                "snapshot_update",
                "snapshot_delete",
                "snapshot_invalid_insert",
                "workflow_task_candidate_pool",
                "workflow_task_claim",
                "Concurrent Snapshot insert was not a clean single winner",
                "ERROR 1213|ERROR 1205");
    }

    @Test
    void scriptMustPinAllThreeFrozenMigrationHashes() throws Exception {
        String value = Files.readString(script);
        assertThat(value).contains(
                "fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2",
                "2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6",
                "ee16801af2f989449b1372ff82d76bd9850950a51ea7badff0125804c0815b06");
    }
}
