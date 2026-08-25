package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V2622InstanceRoleResolverBindingMigrationContractTest {
    private static final Path ROOT = Path.of("../database/migration/mysql");
    private static final Path MIGRATION = ROOT.resolve(
            "V2.6.22__extend_workflow_instance_role_resolver_binding.sql");

    @Test
    void migrationMustExtendRoleFreezeWithoutWeakeningLegacyOrAppendOnlyGovernance()
            throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains(
                "MODIFY COLUMN manifest_version VARCHAR(64) NOT NULL",
                "version_binding_id BIGINT NULL",
                "version_binding_order INT NULL",
                "version_binding_hash VARCHAR(64)",
                "resolver_contract_hash_snapshot VARCHAR(64)",
                "role_code VARCHAR(100)",
                "organization_scope_type VARCHAR(40)",
                "resolved_organization_id BIGINT NULL",
                "effective_time_policy VARCHAR(40)",
                "binding_schema_version VARCHAR(64)",
                "strategy_type = 'USER'",
                "resolver_mode = 'DIRECT'",
                "strategy_type = 'ROLE'",
                "resolver_mode = 'CANDIDATE_POOL'",
                "organization_scope_type = 'FIXED_ORG'",
                "fk_workflow_node_resolver_version_binding",
                "trg_workflow_node_resolver_snapshot_no_update",
                "trg_workflow_node_resolver_snapshot_no_delete",
                "WORKFLOW_NODE_RESOLVER_SNAPSHOT_IMMUTABLE");
        assertThat(sql).doesNotContain(
                "directory_revision", "directory_result_hash", "candidate_user_ids",
                "CREATE TABLE workflow_instance_role_binding_set");
    }

    @Test
    void versionMustBeUniqueAndAllHistoricalMigrationHashesMustRemainValid()
            throws Exception {
        long v2622 = Files.list(ROOT)
                .filter(path -> path.getFileName().toString().startsWith("V2.6.22__"))
                .count();
        assertThat(v2622).isEqualTo(1);
        String sums = Files.readString(ROOT.resolve("SHA256SUMS"), StandardCharsets.UTF_8);
        assertThat(sums).contains(
                "cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e  V2.6.21__create_workflow_version_resolver_binding.sql",
                "V2.6.22__extend_workflow_instance_role_resolver_binding.sql");
    }
}
