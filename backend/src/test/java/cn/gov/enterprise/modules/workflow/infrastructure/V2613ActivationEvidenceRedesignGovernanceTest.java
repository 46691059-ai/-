package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V2613ActivationEvidenceRedesignGovernanceTest {
    private static final Path ROOT = Path.of("..", "database");

    @Test
    void failedCandidateMustRemainArchivedOutsideFlywayScanRoot() throws Exception {
        Path archive = ROOT.resolve(Path.of("migration", "archive", "failed-candidates",
                "V2.6.13__create_role_runtime_activation_evidence__failed_7ec6ec64.sql"));
        assertThat(archive).exists();
        assertThat(sha256(archive)).isEqualTo(
                "7ec6ec6441006dc7f114b92adfc60efdfb2cc8f66bda3a6a7a52d9fcbf3168e6");
        assertThat(archive.toString()).doesNotContain("migration\\mysql");
    }

    @Test
    void redesignedCandidateMustNotIntroduceV2614OrRuntimeObjects() throws Exception {
        Path sql = ROOT.resolve(Path.of("migration", "mysql",
                "V2.6.13__create_role_runtime_activation_evidence.sql"));
        String content = Files.readString(sql);
        assertThat(ROOT.resolve(Path.of("migration", "mysql",
                "V2.6.14__fix_role_runtime_activation_evidence_integrity.sql"))).doesNotExist();
        assertThat(content).contains("tmp_role_activation_v2613_guard")
                .contains("fk_role_activation_evidence_approval")
                .contains("trg_role_activation_approval_insert_guard")
                .contains("trg_role_activation_evidence_insert_guard")
                .doesNotContain("CREATE TABLE workflow_task")
                .doesNotContain("CREATE TABLE workflow_candidate_pool")
                .doesNotContain("investment_");
    }

    private static String sha256(Path file) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file));
        return java.util.HexFormat.of().formatHex(digest);
    }
}
