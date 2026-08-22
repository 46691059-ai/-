package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowLinearRuntimeMigrationContractTest {
    private static final Path MIGRATION = Path.of("..", "database", "migration", "mysql",
            "V2.6.1__enable_workflow_linear_runtime.sql");

    @Test
    void migrationMustAddOnlyLinearRuntimeAssociations() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains("MULTI_NODE_LINEAR_V1")
                .contains("GRAPH_V2_SHA256")
                .contains("current_node_execution_id")
                .contains("node_execution_id")
                .contains("fk_workflow_task_execution")
                .contains("uk_workflow_task_execution_participant")
                .doesNotContain("investment_")
                .doesNotContain("workflow_condition")
                .doesNotContain("workflow_countersign")
                .doesNotContain("CREATE TABLE");
    }

    @Test
    void migrationMustPreserveLegacyRowsAndUseChecksAndForeignKeys() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains("DEFAULT 'SINGLE_NODE_LEGACY'")
                .contains("DEFAULT 'NODE_V1_SHA256'")
                .contains("current_node_execution_id BIGINT NULL")
                .contains("node_execution_id BIGINT NULL")
                .contains("chk_workflow_instance_engine_hash_pair")
                .contains("FOREIGN KEY (id, current_node_execution_id)")
                .contains("FOREIGN KEY (instance_id, version_id, node_id, node_execution_id)");
    }
}
