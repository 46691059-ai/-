package cn.gov.enterprise.modules.project.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

public class LifecycleV2MigrationTest {
    private static final Path MIGRATION_DIR = Path.of(
            "..", "database", "mysql", "migration").normalize();
    private static final List<String> MIGRATIONS = List.of(
            "V2.1.0__create_project_lifecycle_v2_structure.sql",
            "V2.1.1__link_project_stage_lifecycle_snapshot.sql",
            "V2.1.2__backfill_legacy_project_lifecycle.sql",
            "V2.1.3__seed_project_lifecycle_templates.sql");

    @Test
    void shouldDeclareOrderedImmutableMigrationChain() throws Exception {
        assertThat(MIGRATIONS).allSatisfy(file -> assertThat(MIGRATION_DIR.resolve(file)).exists());
        String baselineHashBefore = sha256(Path.of(
                "..", "database", "mysql", "05_project.sql").normalize());

        String structure = read(MIGRATIONS.get(0));
        String link = read(MIGRATIONS.get(1));
        String legacy = read(MIGRATIONS.get(2));
        String seed = read(MIGRATIONS.get(3));

        assertThat(structure)
                .contains("CREATE TABLE project_lifecycle_template")
                .contains("CREATE TABLE project_lifecycle_template_version")
                .contains("CREATE TABLE project_lifecycle_instance")
                .contains("CREATE TABLE project_lifecycle_stage_snapshot");
        assertThat(link).contains("ADD COLUMN lifecycle_instance_id")
                .contains("ADD COLUMN stage_snapshot_id");
        assertThat(legacy).contains("'LEGACY', NULL, NULL")
                .doesNotContain("project_stage_template")
                .contains("source_stage_template_id")
                .contains("NULL, 'UNCONFIRMED'");
        assertThat(seed).contains("'INVESTMENT_STANDARD'")
                .contains("'DELIVERY_STANDARD'")
                .contains("'ACTIVE'");
        assertThat(sha256(Path.of(
                "..", "database", "mysql", "05_project.sql").normalize()))
                .isEqualTo(baselineHashBefore);
    }

    @Test
    void shouldApplyStructureAndSeedToEmptyH2MysqlCompatibilityDatabase() throws Exception {
        try (Connection connection = openDatabase("lifecycle_v2_empty")) {
            createBaselinePrerequisites(connection);
            executeMigration(connection, MIGRATIONS.get(0));
            executeMigration(connection, MIGRATIONS.get(1));
            executeMigration(connection, MIGRATIONS.get(2));
            executeMigration(connection, MIGRATIONS.get(3));

            assertThat(count(connection, "project_lifecycle_template")).isEqualTo(2);
            assertThat(count(connection, "project_lifecycle_template_active")).isEqualTo(2);
            assertThat(count(connection, "project_lifecycle_stage_template")).isEqualTo(16);
            assertThat(columnExists(connection, "project_stage", "lifecycle_instance_id")).isTrue();
            assertThat(columnExists(connection, "project_stage", "stage_snapshot_id")).isTrue();
        }
    }

    @Test
    void shouldBackfillLegacyProjectWithoutInferringTemplate() throws Exception {
        try (Connection connection = openDatabase("lifecycle_v2_legacy")) {
            createBaselinePrerequisites(connection);
            executeMigration(connection, MIGRATIONS.get(0));
            executeMigration(connection, MIGRATIONS.get(1));
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                    INSERT INTO project_info
                        (id, status, create_time, create_by, deleted)
                    VALUES (100, 'IN_PROGRESS', CURRENT_TIMESTAMP, 'legacy-user', 0)
                    """);
                statement.execute("""
                    INSERT INTO project_stage
                        (id, project_id, stage_code, stage_name, stage_order,
                         create_time, create_by, update_time, deleted, delete_token)
                    VALUES
                        (101, 100, 'CUSTOM_A', '历史阶段A', 1,
                         CURRENT_TIMESTAMP, 'legacy-user', CURRENT_TIMESTAMP, 0, 0),
                        (102, 100, 'CUSTOM_B', '历史阶段B', 2,
                         CURRENT_TIMESTAMP, 'legacy-user', CURRENT_TIMESTAMP, 0, 0)
                    """);
            }

            executeMigration(connection, MIGRATIONS.get(2));

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                     SELECT source_type, template_id, template_version_id, snapshot_status
                     FROM project_lifecycle_instance WHERE project_id = 100
                     """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("source_type")).isEqualTo("LEGACY");
                assertThat(result.getObject("template_id")).isNull();
                assertThat(result.getObject("template_version_id")).isNull();
                assertThat(result.getString("snapshot_status")).isEqualTo("READY");
            }
            assertThat(count(connection, "project_lifecycle_stage_snapshot")).isEqualTo(2);
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                     SELECT COUNT(*) FROM project_stage
                     WHERE project_id = 100
                       AND lifecycle_instance_id = 100
                       AND stage_snapshot_id IS NOT NULL
                     """)) {
                result.next();
                assertThat(result.getLong(1)).isEqualTo(2);
            }
        }
    }

    private Connection openDatabase(String name) throws Exception {
        Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + name
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE ALIAS SHA2 FOR '"
                    + LifecycleV2MigrationTest.class.getName() + ".sha2Sql'");
        }
        return connection;
    }

    private void createBaselinePrerequisites(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE project_info (
                    id BIGINT PRIMARY KEY, status VARCHAR(30),
                    create_time TIMESTAMP(3), create_by VARCHAR(64), deleted SMALLINT DEFAULT 0
                )
                """);
            statement.execute("""
                CREATE TABLE project_stage (
                    id BIGINT PRIMARY KEY, project_id BIGINT NOT NULL,
                    stage_code VARCHAR(50), stage_name VARCHAR(100), stage_order INT,
                    create_time TIMESTAMP(3), create_by VARCHAR(64),
                    update_time TIMESTAMP(3), update_by VARCHAR(64),
                    deleted SMALLINT DEFAULT 0, delete_token BIGINT DEFAULT 0
                )
                """);
        }
    }

    private void executeMigration(Connection connection, String file) throws Exception {
        String sql = read(file)
                .replaceAll("(?m)^\\s*--.*$", "")
                .replaceAll("(?im)^\\s*USE\\s+enterprise_platform\\s*;", "")
                .replaceAll(
                        "(?is)\\)\\s*ENGINE=InnoDB\\s+DEFAULT\\s+CHARSET=utf8mb4\\s+COMMENT='[^']*'\\s*;",
                        ");")
                .replace("CURRENT_TIMESTAMP(3)", "CURRENT_TIMESTAMP");
        for (String fragment : sql.split(";")) {
            String statementSql = fragment.trim();
            if (statementSql.isEmpty() || statementSql.equalsIgnoreCase("START TRANSACTION")
                    || statementSql.equalsIgnoreCase("COMMIT")) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(statementSql);
            }
        }
    }

    private long count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private boolean columnExists(Connection connection, String table, String column)
            throws Exception {
        try (ResultSet result = connection.getMetaData().getColumns(
                null, null, table, column)) {
            return result.next();
        }
    }

    private String read(String file) throws Exception {
        return Files.readString(MIGRATION_DIR.resolve(file), StandardCharsets.UTF_8);
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    public static String sha2Sql(String value, int bits) throws Exception {
        if (bits != 256) throw new IllegalArgumentException("Only SHA-256 is supported");
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
