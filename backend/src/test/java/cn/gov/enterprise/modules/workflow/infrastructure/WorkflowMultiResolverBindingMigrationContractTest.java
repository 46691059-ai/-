package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowMultiResolverBindingMigrationContractTest {
    private final Path migration = Path.of("../database/migration/mysql/"
            + "V2.6.5__create_workflow_multi_resolver_binding.sql");

    @Test
    void createsThreeFrozenFactTablesWithoutLegacyBackfill() throws Exception {
        String sql = Files.readString(migration);
        assertThat(sql).contains("CREATE TABLE workflow_instance_resolver_binding_set",
                "CREATE TABLE workflow_instance_resolver_binding",
                "CREATE TABLE workflow_node_resolver_binding_snapshot",
                "CHARACTER SET ascii COLLATE ascii_bin", "delete_token", "version INT",
                "FOREIGN KEY", "CHECK", "UNIQUE KEY");
        assertThat(sql).doesNotContain("UPDATE workflow_instance", "ALTER TABLE workflow_instance",
                "workflow_task_candidate_pool", "workflow_task_claim");
    }

    @Test
    void assetReflectsValidatedCanonicalBaseline() throws Exception {
        String inventory = Files.readString(Path.of("../database/flyway/migration-inventory.yml"));
        assertThat(inventory).contains("V2.6.5__create_workflow_multi_resolver_binding.sql",
                "asset_status: \"CANONICAL_IMMUTABLE\"",
                "execution_status: \"EPHEMERAL_MYSQL8_VALIDATED\"",
                "flyway_checksum: -588626998");
    }
}
