package cn.gov.enterprise.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OutputCaptureExtension.class)
@EnabledIfEnvironmentVariable(named = "RC2_MAPPING_DB_URL", matches = ".+")
class DatabaseMappingCheckerRealMysqlTest {

    @Test
    void mysqlGeneratedTerminalTokensAreAnAllowedDatabaseSuperset(CapturedOutput output)
            throws Exception {
        String url = System.getenv("RC2_MAPPING_DB_URL");
        String user = System.getenv().getOrDefault("RC2_MAPPING_DB_USERNAME", "root");
        String password = System.getenv().getOrDefault("RC2_MAPPING_DB_PASSWORD", "");
        Map<String, String> generated = new LinkedHashMap<>();

        try (var connection = DriverManager.getConnection(url, user, password);
                var statement = connection.prepareStatement("""
                        SELECT column_name, extra, generation_expression
                          FROM information_schema.columns
                         WHERE table_schema=DATABASE()
                           AND table_name='workflow_role_runtime_execution_admission_event'
                           AND column_name IN ('decision_terminal_token','closure_terminal_token')
                         ORDER BY ordinal_position
                        """);
                var rows = statement.executeQuery()) {
            while (rows.next()) {
                assertThat(rows.getString("extra")).isEqualTo("STORED GENERATED");
                assertThat(rows.getString("generation_expression")).isNotBlank();
                generated.put(rows.getString("column_name"), rows.getString("generation_expression"));
            }
        }
        assertThat(generated).containsOnlyKeys("decision_terminal_token", "closure_terminal_token");

        var dataSource = new DriverManagerDataSource(url, user, password);
        new DatabaseMappingChecker(dataSource, true).run(new DefaultApplicationArguments(new String[0]));

        assertThat(output).contains(
                "WorkflowRoleRuntimeExecutionAdmissionEventEntity",
                "result=ALLOWED_GENERATED_DATABASE_SUPERSET",
                "decision_terminal_token",
                "closure_terminal_token");
        assertThat(output).doesNotContain(
                "WorkflowRoleRuntimeExecutionAdmissionEventEntity, table=workflow_role_runtime_execution_admission_event, result=INCONSISTENT");
    }
}
