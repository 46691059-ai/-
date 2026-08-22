package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V2613ActivationEvidenceMigrationContractTest {
    private static final Path SQL = Path.of("..", "database", "migration", "mysql",
            "V2.6.13__create_role_runtime_activation_evidence.sql");

    @Test
    void candidateMustDefineGuardTablesConstraintsAndAppendOnlyTriggers() throws Exception {
        String sql = Files.readString(SQL);
        assertThat(sql).contains("tmp_role_activation_v2613_guard")
                .contains("role_runtime_activation_request")
                .contains("role_runtime_activation_approval")
                .contains("role_runtime_activation_evidence")
                .contains("ROLE_RUNTIME_ACTIVATION_CANONICAL_V1")
                .contains("approval_id BIGINT NOT NULL")
                .contains("FOREIGN KEY (activation_id, delete_token)")
                .contains("FOREIGN KEY (approval_id, activation_id, delete_token)")
                .contains("ROLE_RUNTIME_ACTIVATION_APPROVAL_HASH_MISMATCH")
                .contains("ROLE_RUNTIME_ACTIVATION_EVIDENCE_HASH_MISMATCH")
                .contains("CHARACTER SET ascii COLLATE ascii_bin")
                .contains("BEFORE UPDATE ON role_runtime_activation_request")
                .contains("BEFORE DELETE ON role_runtime_activation_request")
                .contains("BEFORE UPDATE ON role_runtime_activation_approval")
                .contains("BEFORE DELETE ON role_runtime_activation_approval")
                .contains("BEFORE UPDATE ON role_runtime_activation_evidence")
                .contains("BEFORE DELETE ON role_runtime_activation_evidence");
        assertThat(sql).doesNotContain("workflow_task (")
                .doesNotContain("workflow_candidate_pool (")
                .doesNotContain("investment_");
    }
}
