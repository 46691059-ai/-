package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class V2623RealtimeEligibilityRemarkMigrationContractTest {
    private static final Path ROOT = Path.of("../database/migration/mysql");
    private static final Path MIGRATION = ROOT.resolve(
            "V2.6.23__add_workflow_realtime_eligibility_remark.sql");
    private static final List<String> TARGETS = List.of(
            "workflow_role_realtime_eligibility_capability_evidence",
            "workflow_role_realtime_eligibility_event",
            "workflow_role_realtime_eligibility_validator_evidence");

    @Test
    void candidateIsUniqueAndOnlyAddsTheThreeNullableRemarkColumns() throws Exception {
        assertThat(MIGRATION).exists();
        try (var files = Files.list(ROOT)) {
            assertThat(files.filter(path -> path.getFileName().toString()
                    .startsWith("V2.6.23__"))).hasSize(1);
        }
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(occurrences(sql, "ALTER TABLE ")).isEqualTo(3);
        assertThat(occurrences(sql, "ADD COLUMN remark VARCHAR(500) NULL AFTER delete_token"))
                .isEqualTo(3);
        TARGETS.forEach(table -> assertThat(sql).contains("ALTER TABLE " + table));
        assertThat(sql).doesNotContain(
                "IF NOT EXISTS", "DEFAULT ''", "NOT NULL", "CREATE INDEX",
                "ADD KEY", "ADD CONSTRAINT", "CREATE TRIGGER", "UPDATE ",
                "DELETE ", "INSERT ", "MODIFY COLUMN", "CHANGE COLUMN");
    }

    @Test
    void auditedEntitiesInheritRemarkWithoutShadowFields() throws Exception {
        Path entities = Path.of("src/main/java/cn/gov/enterprise/modules/workflow/"
                + "infrastructure/persistence/entity");
        String base = Files.readString(entities.resolve("WorkflowAuditedEntity.java"));
        assertThat(base).contains("private String remark;");
        for (String name : List.of(
                "WorkflowRoleRealtimeEligibilityCapabilityEvidenceEntity.java",
                "WorkflowRoleRealtimeEligibilityEventEntity.java",
                "WorkflowRoleRealtimeEligibilityValidatorEvidenceEntity.java")) {
            String source = Files.readString(entities.resolve(name));
            assertThat(source).contains("extends WorkflowAuditedEntity");
            assertThat(source).doesNotContain("String remark", "exist = false");
        }
    }

    @Test
    void historicalMigrationHashesRemainUnchangedAndCandidateIsGoverned() throws Exception {
        List<String> lines = Files.readAllLines(ROOT.resolve("SHA256SUMS"),
                StandardCharsets.UTF_8);
        assertThat(lines).hasSize(45);
        assertThat(lines).contains(
                "cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e"
                        + "  V2.6.21__create_workflow_version_resolver_binding.sql",
                "658b4939a7e92295527ef3a5116d20814b0d17e4c34e46f3955e4c21c67dcc05"
                        + "  V2.6.22__extend_workflow_instance_role_resolver_binding.sql");
        for (String line : lines) {
            String[] parts = line.split("  ", 2);
            assertThat(parts).hasSize(2);
            assertThat(sha256(ROOT.resolve(parts[1]))).as(parts[1]).isEqualTo(parts[0]);
        }
    }

    private static int occurrences(String source, String target) {
        return (source.length() - source.replace(target, "").length()) / target.length();
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}
