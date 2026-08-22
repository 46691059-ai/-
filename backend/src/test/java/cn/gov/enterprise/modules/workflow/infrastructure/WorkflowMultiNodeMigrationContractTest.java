package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowMultiNodeMigrationContractTest {
    private static final Path MIGRATION = Path.of("..", "database", "migration", "mysql",
            "V2.6.0__create_workflow_multi_node_foundation.sql");

    @Test
    void migrationMustCreateOnlyTheMinimalMultiNodeFoundation() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains("CREATE TABLE workflow_transition")
                .contains("CREATE TABLE workflow_node_execution")
                .doesNotContain("ALTER TABLE workflow_instance")
                .doesNotContain("ALTER TABLE workflow_task")
                .doesNotContain("investment_")
                .doesNotContain("CREATE TABLE workflow_condition")
                .doesNotContain("CREATE TABLE workflow_countersign");
    }

    @Test
    void migrationMustEnforceOwnershipUniquenessAndStateConstraints() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains("uk_workflow_transition_code")
                .contains("uk_workflow_transition_route")
                .contains("fk_workflow_transition_from_node")
                .contains("fk_workflow_transition_to_node")
                .contains("uk_workflow_node_execution_visit")
                .contains("fk_workflow_node_execution_previous")
                .contains("chk_workflow_node_execution_status")
                .contains("chk_workflow_node_execution_times")
                .contains("deleted SMALLINT NOT NULL")
                .contains("delete_token BIGINT NOT NULL")
                .contains("version INT NOT NULL");
    }
}
