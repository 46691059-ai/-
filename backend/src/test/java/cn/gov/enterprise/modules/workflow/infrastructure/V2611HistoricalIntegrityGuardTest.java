package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V2611HistoricalIntegrityGuardTest {
    private final Path migration = Path.of("../database/migration/mysql/"
            + "V2.6.11__repair_role_runtime_historical_integrity_guard.sql");

    @Test
    void guardMustRunBeforeEveryPermanentDdl() throws Exception {
        String sql = Files.readString(migration);
        int guard = sql.indexOf("CREATE TEMPORARY TABLE tmp_role_runtime_v2611_guard");
        int guardInsert = sql.indexOf("INSERT INTO tmp_role_runtime_v2611_guard");
        int guardDrop = sql.indexOf("DROP TEMPORARY TABLE tmp_role_runtime_v2611_guard");
        int firstPermanentDdl = sql.indexOf("CREATE TRIGGER");

        assertThat(guard).isGreaterThanOrEqualTo(0);
        assertThat(guardInsert).isGreaterThan(guard);
        assertThat(guardDrop).isGreaterThan(guardInsert);
        assertThat(firstPermanentDdl).isGreaterThan(guardDrop);
        assertThat(sql).contains("chk_role_runtime_v2611_guard CHECK (violation_count = 0)");
    }

    @Test
    void guardMustRejectInvalidContractHashAndApprovalSnapshotDrift() throws Exception {
        String sql = Files.readString(migration);
        assertThat(sql).contains(
                "approval.contract_hash IS NULL",
                "snapshot.contract_hash IS NULL",
                "snapshot.binding_hash IS NULL",
                "approval.id IS NULL",
                "approval.status <> 'APPROVED'",
                "BINARY approval.resolver_code <> BINARY snapshot.resolver_code",
                "BINARY approval.resolver_version <> BINARY snapshot.resolver_version",
                "BINARY approval.contract_hash <> BINARY snapshot.contract_hash",
                "NOT REGEXP '^[0-9a-f]{64}$'");
    }

    @Test
    void guardAndInsertTriggerMustRecomputeCanonicalV1Hash() throws Exception {
        String sql = Files.readString(migration);
        assertThat(sql).contains(
                "ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1",
                "SHA2(",
                "candidateRuleHash",
                "directoryRevision",
                "effectiveAt",
                "organizationId",
                "resolverCode",
                "resolverVersion",
                "roleCode",
                "roleRuleHash",
                "sourceEvidenceHash",
                "BINARY snapshot.binding_hash",
                "BINARY computed_binding_hash <> BINARY NEW.binding_hash",
                "ROLE_RUNTIME_SNAPSHOT_CANONICAL_HASH_MISMATCH");
        assertThat(count(sql, "ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void appendOnlyTriggersMustCoverApprovalAndSnapshot() throws Exception {
        String sql = Files.readString(migration);
        assertThat(sql).contains(
                "CREATE TRIGGER trg_role_runtime_approval_no_delete",
                "BEFORE DELETE ON role_runtime_binding_approval",
                "ROLE_RUNTIME_APPROVAL_APPEND_ONLY",
                "CREATE TRIGGER trg_workflow_role_runtime_snapshot_canonical_guard");
        assertThat(sql).doesNotContain(
                "UPDATE role_runtime_binding_approval SET",
                "UPDATE workflow_role_runtime_binding_snapshot SET",
                "DELETE FROM role_runtime_binding_approval WHERE",
                "DELETE FROM workflow_role_runtime_binding_snapshot WHERE",
                "INSERT INTO workflow_task",
                "INSERT INTO workflow_task_candidate_pool",
                "investment_");
    }

    @Test
    void v2611MustBePromotedOnlyWithV2612AndRuntimeRemainDisabled() throws Exception {
        String inventory = Files.readString(Path.of("../database/flyway/migration-inventory.yml"));
        assertThat(inventory).contains(
                "V2.6.11__repair_role_runtime_historical_integrity_guard.sql",
                "asset_status: \"CANONICAL_IMMUTABLE\"",
                "execution_status: \"VALIDATED_WITH_V2612\"",
                "flyway_checksum: 1342469954",
                "depends_on: [\"2.6.10\"]");

        String resolver = Files.readString(Path.of("src/main/java/cn/gov/enterprise/modules/"
                + "workflow/domain/role/RoleDirectoryResolver.java"));
        assertThat(resolver).contains("ResolverStatus.PREPARED, false");
    }

    private static int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
