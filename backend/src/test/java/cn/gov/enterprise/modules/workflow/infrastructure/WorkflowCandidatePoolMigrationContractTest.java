package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowCandidatePoolMigrationContractTest {
    private final Path migration = Path.of("../database/migration/mysql/"
            + "V2.6.6__create_workflow_candidate_pool.sql");

    @Test
    void migrationMustCreateCandidateFoundationAndPreserveDirectContract() throws Exception {
        String sql = Files.readString(migration);
        assertThat(sql).contains("CREATE TABLE workflow_task_candidate_pool",
                "CREATE TABLE workflow_task_candidate_member",
                "assignment_mode IN ('DIRECT', 'CANDIDATE_POOL')",
                "strategy_type = 'USER' AND resolver_mode = 'DIRECT'",
                "strategy_type IN ('ROLE', 'POSITION', 'ORG')",
                "UNIQUE KEY uk_workflow_candidate_pool_task (task_id, delete_token)",
                "UNIQUE KEY uk_workflow_candidate_member_user",
                "CHARACTER SET ascii COLLATE ascii_bin", "FOREIGN KEY", "CHECK",
                "delete_token", "version INT");
        assertThat(sql).doesNotContain("workflow_task_claim", "workflow:claim",
                "UPDATE workflow_task SET assignment_mode = 'CANDIDATE_POOL'");
    }

    @Test
    void migrationMustReplaceNamedChecksInsteadOfModifyingHistory() throws Exception {
        String sql = Files.readString(migration);
        assertThat(sql).contains("DROP CHECK chk_workflow_resolver_binding_mode",
                "DROP CHECK chk_workflow_node_resolver_binding_strategy",
                "ADD CONSTRAINT chk_workflow_resolver_binding_mode",
                "ADD CONSTRAINT chk_workflow_node_resolver_binding_strategy");
        assertThat(Files.readString(Path.of("../database/migration/mysql/"
                + "V2.6.5__create_workflow_multi_resolver_binding.sql")))
                .contains("chk_workflow_resolver_binding_mode CHECK (resolver_mode IN ('DIRECT'))");
    }

    @Test
    void assetMustReflectValidatedCanonicalBaseline() throws Exception {
        String inventory = Files.readString(Path.of("../database/flyway/migration-inventory.yml"));
        assertThat(inventory).contains("V2.6.6__create_workflow_candidate_pool.sql",
                "asset_status: \"CANONICAL_IMMUTABLE\"",
                "execution_status: \"EPHEMERAL_MYSQL8_VALIDATED\"",
                "flyway_checksum: 398007297");
    }
}
