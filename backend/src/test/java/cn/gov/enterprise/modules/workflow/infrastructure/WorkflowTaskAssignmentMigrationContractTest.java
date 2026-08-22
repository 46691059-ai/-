package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowTaskAssignmentMigrationContractTest {
    private static final Path MIGRATION = Path.of("..", "database", "migration", "mysql",
            "V2.6.2__create_workflow_task_assignment_snapshot.sql");

    @Test
    void migrationMustCreateOnlyAssignmentSnapshotFoundation() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains("CREATE TABLE workflow_task_assignment_snapshot")
                .contains("task_id BIGINT NOT NULL")
                .contains("strategy_type VARCHAR(30) NOT NULL")
                .contains("target_type VARCHAR(30) NOT NULL")
                .contains("target_snapshot TEXT NOT NULL")
                .contains("resolved_users TEXT NOT NULL")
                .contains("resolve_time DATETIME(3) NOT NULL")
                .contains("audit_info TEXT NOT NULL")
                .contains("delete_token BIGINT NOT NULL")
                .contains("version INT NOT NULL")
                .doesNotContain("investment_")
                .doesNotContain("CREATE TABLE workflow_role")
                .doesNotContain("CREATE TABLE workflow_position")
                .doesNotContain("CREATE TABLE workflow_org");
    }

    @Test
    void migrationMustEnforceTaskOwnershipAndImmutabilityKeys() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains("uk_workflow_task_assignment_task")
                .contains("FOREIGN KEY (task_id, instance_id, version_id, node_id, node_execution_id)")
                .contains("REFERENCES workflow_task(id, instance_id, version_id, node_id, node_execution_id)")
                .contains("chk_workflow_task_assignment_delete_token")
                .contains("chk_workflow_task_assignment_optimistic");
    }
}
