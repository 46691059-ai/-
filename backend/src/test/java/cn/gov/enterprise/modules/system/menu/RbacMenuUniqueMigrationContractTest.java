package cn.gov.enterprise.modules.system.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RbacMenuUniqueMigrationContractTest {
    private static final Path V253 = Path.of("..", "database", "migration", "mysql",
            "V2.5.3__initialize_workflow_rbac.sql");
    private static final Path V254 = Path.of("..", "database", "migration", "mysql",
            "V2.5.4__govern_rbac_menu_unique_key.sql");
    private static final String V253_SHA256 =
            "6d6904da8028830cdc8fa7b9125994d37d2c916a09277ae9d4526f5646144e26";
    private static final Pattern MAPPING_ROW = Pattern.compile(
            "(?m)^\\s*\\((\\d+),.*?,\\s*'([a-z][a-z0-9_.]+)'\\)[,;]$");

    @Test
    void shouldExplicitlyMapAllReviewedMenusWithoutDuplicateBusinessCodes() throws IOException {
        Map<Long, String> mappings = mappings();

        assertThat(mappings).hasSize(71);
        assertThat(mappings.values()).doesNotHaveDuplicates();
        assertThat(mappings.keySet()).doesNotContainNull();
        assertThat(mappings.values()).allMatch(code ->
                code.matches("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){0,7}$"));
    }

    @Test
    void workflowMenusShouldUseTheFrozenTenStableCodes() throws IOException {
        Map<Long, String> mappings = mappings();
        Map<Long, String> workflow = new LinkedHashMap<>();
        mappings.forEach((id, code) -> {
            if (id >= 25300 && id <= 25332) workflow.put(id, code);
        });

        assertThat(workflow).containsExactlyInAnyOrderEntriesOf(Map.of(
                25300L, "workflow",
                25310L, "workflow.definition",
                25311L, "workflow.definition.create",
                25312L, "workflow.definition.edit",
                25313L, "workflow.definition.publish",
                25320L, "workflow.instance",
                25321L, "workflow.instance.start",
                25330L, "workflow.task",
                25331L, "workflow.task.approve",
                25332L, "workflow.task.withdraw"));
    }

    @Test
    void migrationShouldPreflightBeforePermanentDdlAndEnforceTheFrozenKey() throws IOException {
        String sql = Files.readString(V254, StandardCharsets.UTF_8);
        int diagnostics = sql.indexOf("-- Diagnostic result sets");
        int assertions = sql.indexOf("-- Phase B-D");
        int firstPermanentDdl = sql.indexOf("-- Phase E-F");

        assertThat(diagnostics).isGreaterThan(0);
        assertThat(assertions).isGreaterThan(diagnostics);
        assertThat(firstPermanentDdl).isGreaterThan(assertions);
        assertThat(sql.indexOf("ALTER TABLE sys_menu")).isGreaterThan(firstPermanentDdl);
        assertThat(sql).contains(
                        "ADD COLUMN menu_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL",
                        "MODIFY COLUMN menu_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL",
                        "ADD UNIQUE KEY uk_sys_menu_code (menu_code, delete_token)",
                        "unmapped_sys_menu",
                        "mapping_fingerprint_mismatch",
                        "duplicate_active_route_identity",
                        "duplicate_active_button_identity")
                .doesNotContain("chk_sys_menu_deleted_token");
    }

    @Test
    void temporaryTablesAndMappingStringsMustDeclareTheTargetCollation() throws IOException {
        String sql = Files.readString(V254, StandardCharsets.UTF_8);
        String targetCollation = "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci";

        assertThat(sql).contains(
                "expected_menu_type VARCHAR(20) " + targetCollation,
                "expected_menu_name VARCHAR(100) " + targetCollation,
                "expected_path VARCHAR(200) " + targetCollation,
                "expected_permission VARCHAR(200) " + targetCollation,
                "menu_code VARCHAR(128) " + targetCollation,
                "assertion_name VARCHAR(128) " + targetCollation);
        assertThat(countOccurrences(sql,
                "ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"))
                .isEqualTo(2);
    }

    @Test
    void v253MustRemainByteIdentical() throws IOException, NoSuchAlgorithmException {
        byte[] content = Files.readAllBytes(V253);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        assertThat(java.util.HexFormat.of().formatHex(digest)).isEqualTo(V253_SHA256);
    }

    private Map<Long, String> mappings() throws IOException {
        String sql = Files.readString(V254, StandardCharsets.UTF_8);
        String mappingBlock = sql.substring(0, sql.indexOf("-- Diagnostic result sets"));
        Matcher matcher = MAPPING_ROW.matcher(mappingBlock);
        Map<Long, String> result = new LinkedHashMap<>();
        List<Long> duplicateIds = new ArrayList<>();
        while (matcher.find()) {
            Long id = Long.valueOf(matcher.group(1));
            if (result.put(id, matcher.group(2)) != null) duplicateIds.add(id);
        }
        assertThat(duplicateIds).isEmpty();
        return result;
    }

    private int countOccurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
