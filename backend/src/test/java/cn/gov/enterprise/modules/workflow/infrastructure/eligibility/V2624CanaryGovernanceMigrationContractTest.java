package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

class V2624CanaryGovernanceMigrationContractTest {
    @Test void candidateDefinesExactAppendOnlyStateLedger() throws Exception {
        Path root=Path.of("").toAbsolutePath().getParent();
        String sql=Files.readString(root.resolve("database/migration/mysql/V2.6.24__create_exact_canary_scope_governance.sql"));
        assertThat(sql).contains("workflow_role_canary_scope_governance","enterprise_id BIGINT NOT NULL",
                "organization_id BIGINT NOT NULL","definition_id BIGINT NOT NULL","definition_version_id BIGINT NOT NULL",
                "node_id BIGINT NOT NULL","role_code VARCHAR(100)","APPROVED_NOT_ENABLED","CANARY_SCOPE_APPEND_ONLY",
                "CANARY_SCOPE_TRANSITION_INVALID","uk_canary_scope_revision","uk_canary_scope_previous");
        assertThat(sql).doesNotContain("DROP TABLE","UPDATE workflow_role_runtime_governance_control","DELETE FROM");
        String sha=java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String sums=Files.readString(root.resolve("database/migration/mysql/SHA256SUMS"));
        String inventory=Files.readString(root.resolve("database/flyway/migration-inventory.yml"));
        assertThat(sums).contains(sha+"  V2.6.24__create_exact_canary_scope_governance.sql");
        assertThat(inventory).contains("version: \"2.6.24\"","asset_status: \"CANDIDATE\"",
                "execution_status: \"NOT_EXECUTED\"","flyway_checksum: null","sha256: \""+sha+"\"");
    }
}
