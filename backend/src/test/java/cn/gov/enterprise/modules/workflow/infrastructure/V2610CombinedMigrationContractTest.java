package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V2610CombinedMigrationContractTest {
    private final Path validationScript = Path.of("../database/flyway/scripts/validate-v2610-combined.ps1");
    private final Path v269 = Path.of("../database/migration/mysql/V2.6.9__create_role_runtime_snapshot.sql");
    private final Path v2610 = Path.of("../database/migration/mysql/V2.6.10__harden_role_runtime_persistence_integrity.sql");

    @Test
    void validationMustCoverFreshUpgradeForwardFixAndNoOp() throws Exception {
        String script = Files.readString(validationScript);
        assertThat(script).contains(
                "New-IsolatedMysql 'fresh'",
                "New-IsolatedMysql 'upgrade'",
                "New-IsolatedMysql 'forward'",
                "migrate '2.6.8'",
                "migrate '2.6.9'",
                "migrate '2.6.10'",
                "No migration necessary",
                "Fingerprint mismatch");
    }

    @Test
    void validationMustExerciseP0IntegrityAndPreDdlGuards() throws Exception {
        String script = Files.readString(validationScript);
        assertThat(script).contains(
                "01_null_resolver_version",
                "02_blank_resolver_version",
                "03_snapshot_update",
                "04_snapshot_delete",
                "09_pending_approval_snapshot",
                "10_resolver_code_mismatch",
                "13_uppercase_hash",
                "guard-contract",
                "guard-hash",
                "Permanent-Guard-Count");
    }

    @Test
    void validationMustPinFrozenMigrationHashes() throws Exception {
        String script = Files.readString(validationScript);
        assertThat(Files.exists(v269)).isTrue();
        assertThat(Files.exists(v2610)).isTrue();
        assertThat(script).contains(
                "fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2",
                "2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6",
                "ROLE_DIRECTORY_V1");
    }
}
