package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.domain.repository.TaskClaimAuditRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.TaskClaimRepository;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WorkflowClaimIntegrityMigrationContractTest {
    private static final Path MIGRATION = Path.of("..", "database", "migration", "mysql",
            "V2.6.8__strengthen_workflow_claim_integrity.sql");

    @Test
    void migrationMustPrecheckBeforePermanentDdlAndCloseTheOwnershipChain() throws Exception {
        String sql = Files.readString(MIGRATION);
        int guard = sql.indexOf("CREATE TEMPORARY TABLE tmp_v268_integrity_guard");
        int firstPermanentDdl = sql.indexOf("ALTER TABLE workflow_task\n");

        assertThat(guard).isGreaterThanOrEqualTo(0);
        assertThat(firstPermanentDdl).isGreaterThan(guard);
        assertThat(sql.substring(guard, firstPermanentDdl)).contains(
                "CLAIM_NODE_EXECUTION_MISSING_OR_CROSS_INSTANCE",
                "CLAIM_TASK_OWNER_MISMATCH",
                "CLAIM_POOL_OWNER_MISMATCH",
                "CLAIM_MEMBER_OWNER_MISMATCH",
                "SUCCESS_AUDIT_CLAIM_MISSING",
                "SUCCESS_AUDIT_OWNER_MISMATCH",
                "DUPLICATE_ACTIVE_CLAIM");
        assertThat(sql).contains(
                "uk_workflow_task_claim_owner",
                "uk_workflow_candidate_pool_claim_owner",
                "uk_workflow_task_claim_audit_owner",
                "fk_workflow_task_claim_task_execution",
                "fk_workflow_task_claim_node_execution",
                "fk_workflow_task_claim_pool_execution",
                "fk_workflow_task_claim_audit_owner",
                "chk_workflow_task_claim_audit_success_owner");
    }

    @Test
    void migrationMustEncodeAllEighteenFrozenNegativeContracts() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains(
                "FOREIGN KEY (instance_id, node_execution_id)",                 // 1, 2
                "FOREIGN KEY (task_id, instance_id, node_execution_id)",        // 3
                "candidate_pool_id, task_id, instance_id, node_execution_id",   // 4
                "m.id = c.candidate_member_id", "m.pool_id = c.candidate_pool_id", // 5
                "result <> 'SUCCESS'", "claim_id IS NOT NULL",                 // 6
                "REFERENCES workflow_task_claim",                              // 7
                "claim_id, task_id, instance_id, node_execution_id",            // 8-10
                "candidate_pool_id, candidate_member_id",                       // 11, 12
                "trg_workflow_task_claim_audit_no_update",                      // 13-17
                "DUPLICATE_ACTIVE_CLAIM");                                      // 18 precheck
        assertThat(Files.readString(MIGRATION.resolveSibling(
                "V2.6.7__create_workflow_task_claim.sql")))
                .contains("uk_workflow_task_claim_active");
        assertThat(sql).contains("trg_workflow_task_claim_audit_no_delete");
        assertThat(sql).doesNotContain("UPDATE workflow_task_claim_audit SET",
                "DELETE FROM workflow_task_claim", "CREATE TABLE workflow_task_release");
    }

    @Test
    void repositoriesMustExposeOnlyImmutableClaimContracts() {
        Set<String> claimMethods = Arrays.stream(TaskClaimRepository.class.getDeclaredMethods())
                .map(Method::getName).collect(Collectors.toSet());
        Set<String> auditMethods = Arrays.stream(TaskClaimAuditRepository.class.getDeclaredMethods())
                .map(Method::getName).collect(Collectors.toSet());

        assertThat(claimMethods).containsExactlyInAnyOrder(
                "findByTaskIdAndIdempotencyKey", "findActiveByTaskId", "insert");
        assertThat(auditMethods).containsExactly("appendSuccess");
        assertThat(claimMethods).noneMatch(name -> name.toLowerCase().contains("ownership")
                || name.toLowerCase().contains("update"));
        assertThat(auditMethods).noneMatch(name -> name.toLowerCase().contains("update")
                || name.toLowerCase().contains("delete"));
    }

    @Test
    void v267MustRemainByteForByteFrozen() throws Exception {
        byte[] bytes = Files.readAllBytes(MIGRATION.resolveSibling(
                "V2.6.7__create_workflow_task_claim.sql"));
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThat(sha).isEqualTo(
                "800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed");
    }
}
