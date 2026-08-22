package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class V2612ResolverVersionCollationContractTest {
    private static final Pattern STRICT_VERSION =
            Pattern.compile("^[A-Z0-9][A-Z0-9_.-]{0,63}$");
    private final Path migration = Path.of("../database/migration/mysql/"
            + "V2.6.12__fix_role_runtime_resolver_version_collation.sql");

    @Test
    void historicalGuardMustBeCaseSensitiveAndPrecedePermanentDdl() throws Exception {
        String sql = Files.readString(migration);
        int temporaryGuard = sql.indexOf("CREATE TEMPORARY TABLE tmp_role_runtime_v2612_guard");
        int guardScan = sql.indexOf("INSERT INTO tmp_role_runtime_v2612_guard");
        int guardDrop = sql.indexOf("DROP TEMPORARY TABLE tmp_role_runtime_v2612_guard");
        int firstAlter = sql.indexOf("ALTER TABLE role_runtime_binding_approval");

        assertThat(temporaryGuard).isGreaterThanOrEqualTo(0);
        assertThat(guardScan).isGreaterThan(temporaryGuard);
        assertThat(guardDrop).isGreaterThan(guardScan);
        assertThat(firstAlter).isGreaterThan(guardDrop);
        assertThat(sql).contains(
                "approval.resolver_version IS NULL",
                "snapshot.resolver_version IS NULL",
                "BINARY approval.resolver_version <> BINARY TRIM(approval.resolver_version)",
                "BINARY snapshot.resolver_version <> BINARY TRIM(snapshot.resolver_version)",
                "REGEXP_LIKE(",
                "'^[A-Z0-9][A-Z0-9_.-]{0,63}$'",
                "'c'");
    }

    @Test
    void bothResolverVersionColumnsMustUseAsciiBinaryAndStrictChecks() throws Exception {
        String sql = Files.readString(migration);

        assertThat(count(sql, "MODIFY COLUMN resolver_version VARCHAR(64)")).isEqualTo(2);
        assertThat(count(sql, "CHARACTER SET ascii COLLATE ascii_bin NOT NULL")).isEqualTo(2);
        assertThat(sql).contains(
                "DROP CHECK chk_role_runtime_approval_resolver_identity",
                "ADD CONSTRAINT chk_role_runtime_approval_resolver_identity CHECK",
                "DROP CHECK chk_role_runtime_snapshot_resolver_identity",
                "ADD CONSTRAINT chk_role_runtime_snapshot_resolver_identity CHECK");
        assertThat(sql).doesNotContain(
                "utf8mb4_general_ci",
                "LOWER(",
                "UPDATE role_runtime_binding_approval SET",
                "UPDATE workflow_role_runtime_binding_snapshot SET",
                "ROLE_DIRECTORY_V1', 'ACTIVE",
                "investment_");
    }

    @Test
    void canonicalHashMustRemainCaseSensitiveToResolverVersion() throws Exception {
        String upper = sha256("{\"resolverVersion\":\"ROLE_DIRECTORY_V1\","
                + "\"schema\":\"ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1\"}");
        String lower = sha256("{\"resolverVersion\":\"role_directory_v1\","
                + "\"schema\":\"ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1\"}");
        String mixed = sha256("{\"resolverVersion\":\"Role_Directory_V1\","
                + "\"schema\":\"ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1\"}");

        assertThat(upper).hasSize(64).isNotEqualTo(lower).isNotEqualTo(mixed);
        assertThat(lower).isNotEqualTo(mixed);
    }

    @Test
    void uppercaseResolverVersionMustPassTheFrozenFormat() {
        assertThat(valid("ROLE_DIRECTORY_V1")).isTrue();
    }

    @Test
    void lowercaseResolverVersionMustFailTheFrozenFormat() {
        assertThat(valid("role_directory_v1")).isFalse();
    }

    @Test
    void mixedCaseResolverVersionMustFailTheFrozenFormat() {
        assertThat(valid("Role_Directory_V1")).isFalse();
    }

    @Test
    void blankResolverVersionMustFailTheFrozenFormat() {
        assertThat(valid("")).isFalse();
    }

    @Test
    void nullResolverVersionMustFailTheFrozenFormat() {
        assertThat(valid(null)).isFalse();
    }

    @Test
    void existingAppendOnlyTriggersMustRemainTheRuntimeMutationBoundary() throws Exception {
        String v2610 = Files.readString(Path.of("../database/migration/mysql/"
                + "V2.6.10__harden_role_runtime_persistence_integrity.sql"));
        String v2611 = Files.readString(Path.of("../database/migration/mysql/"
                + "V2.6.11__repair_role_runtime_historical_integrity_guard.sql"));

        assertThat(v2610).contains(
                "CREATE TRIGGER trg_role_runtime_approval_no_update",
                "CREATE TRIGGER trg_workflow_role_runtime_snapshot_no_update",
                "CREATE TRIGGER trg_workflow_role_runtime_snapshot_no_delete");
        assertThat(v2611).contains(
                "CREATE TRIGGER trg_role_runtime_approval_no_delete",
                "CREATE TRIGGER trg_workflow_role_runtime_snapshot_canonical_guard");
    }

    @Test
    void assetMustBePromotedOnlyAfterCombinedValidationAndRoleRuntimeRemainDisabled()
            throws Exception {
        String inventory = Files.readString(Path.of("../database/flyway/migration-inventory.yml"));
        assertThat(inventory).contains(
                "V2.6.12__fix_role_runtime_resolver_version_collation.sql",
                "asset_status: \"CANONICAL_IMMUTABLE\"",
                "execution_status: \"EPHEMERAL_MYSQL8_VALIDATED\"",
                "flyway_checksum: -1172180638",
                "depends_on: [\"2.6.11\"]");

        String resolver = Files.readString(Path.of("src/main/java/cn/gov/enterprise/modules/"
                + "workflow/domain/role/RoleDirectoryResolver.java"));
        assertThat(resolver).contains("ResolverStatus.PREPARED, false");
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean valid(String value) {
        return value != null && STRICT_VERSION.matcher(value).matches();
    }

    private static int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
