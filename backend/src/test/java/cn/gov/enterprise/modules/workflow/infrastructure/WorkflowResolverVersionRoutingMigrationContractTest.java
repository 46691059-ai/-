package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowResolverVersionRoutingMigrationContractTest {
    private final Path migration = Path.of("../database/migration/mysql/"
            + "V2.6.3__freeze_workflow_instance_resolver_version.sql");

    @Test
    void migrationMustFreezeResolverIdentityOnLinearInstancesOnly() throws IOException {
        String sql = Files.readString(migration);

        assertThat(sql).contains("ADD COLUMN resolver_code", "ADD COLUMN resolver_version",
                "ADD COLUMN resolver_contract_hash", "MULTI_NODE_LINEAR_V1",
                "EXPLICIT_USER_V1", ExplicitUserResolverHash.VALUE,
                "chk_workflow_instance_resolver_complete",
                "chk_workflow_instance_linear_resolver");
        assertThat(sql).doesNotContain("ALTER TABLE workflow_task_assignment_snapshot");
    }

    @Test
    void migrationMustRemainImmutableAndGovernedWithItsCorrectiveSuccessor() throws IOException {
        String inventory = Files.readString(Path.of("../database/flyway/migration-inventory.yml"));
        assertThat(inventory).contains("V2.6.3__freeze_workflow_instance_resolver_version.sql",
                "asset_status: \"CANONICAL_IMMUTABLE\"",
                "execution_status: \"EPHEMERAL_MYSQL8_VALIDATED_WITH_V264\"");
    }

    private static final class ExplicitUserResolverHash {
        private static final String VALUE =
                "65873eb742d0a20b68f1a5e69cbc8e002eb6de26cfc7b516cbad494aa5ff5b6d";
    }
}
