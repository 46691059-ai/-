package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowVersionReleaseMigrationContractTest {
    private static final Path MIGRATION = Path.of("..", "database", "migration", "mysql",
            "V2.5.5__implement_workflow_version_release.sql");

    @Test
    void migrationMustOnlyAddReleaseAuditAndInstanceHashSnapshot() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains("ADD COLUMN definition_content_hash_snapshot")
                .contains("CREATE TABLE workflow_version_release")
                .contains("workflow_version wv")
                .contains("REGEXP '^[0-9a-f]{64}$'")
                .doesNotContain("CREATE TABLE workflow_definition (")
                .doesNotContain("CREATE TABLE workflow_version (")
                .doesNotContain("investment_");
    }

    @Test
    void migrationMustPreserveOwnershipAndPublicationConstraints() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains("uk_workflow_release_published")
                .contains("fk_workflow_release_previous")
                .contains("fk_workflow_release_published")
                .contains("chk_workflow_release_immutable");
    }
}
