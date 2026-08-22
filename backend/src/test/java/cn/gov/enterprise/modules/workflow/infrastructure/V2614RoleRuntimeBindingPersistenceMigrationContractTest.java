package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeBindingCandidateSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeBindingLifecycleRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeBindingPromotionRepository;
import cn.gov.enterprise.modules.workflow.application.service.RoleRuntimeBindingPersistenceApplicationService;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class V2614RoleRuntimeBindingPersistenceMigrationContractTest {
    private static final Path SQL = Path.of("..", "database", "migration", "mysql",
            "V2.6.14__create_role_runtime_binding_persistence_foundation.sql");
    private static final Path FAILED_CANDIDATE = Path.of("..", "database", "migration", "archive",
            "failed-candidates",
            "V2.6.14__create_role_runtime_binding_persistence_foundation__failed_bcbcbb6b.sql");

    @Test
    void migrationMustGuardBeforeCreatingExactlyThreeAppendOnlyGovernanceTables() throws Exception {
        String sql = Files.readString(SQL);
        int guard = sql.indexOf("CREATE TEMPORARY TABLE tmp_role_binding_v2614_guard");
        int firstDdl = sql.indexOf("CREATE TABLE workflow_role_binding_promotion");
        assertThat(guard).isGreaterThanOrEqualTo(0).isLessThan(firstDdl);
        assertThat(count(sql, "CREATE TABLE workflow_role_binding_")).isEqualTo(3);
        assertThat(sql).contains("workflow_role_binding_promotion",
                "workflow_role_binding_candidate_snapshot",
                "workflow_role_binding_snapshot_event",
                "fk_role_binding_promotion_activation",
                "fk_role_binding_snapshot_promotion",
                "fk_role_binding_event_snapshot");
        assertThat(count(sql, "_no_update")).isEqualTo(3);
        assertThat(count(sql, "_no_delete")).isEqualTo(3);
        assertThat(sql).contains("ROLE_BINDING_PROMOTION_EVIDENCE_INVALID",
                "ROLE_BINDING_SNAPSHOT_PROMOTION_MISMATCH",
                "ROLE_BINDING_EVENT_CHAIN_INVALID", "CHARACTER SET ascii COLLATE ascii_bin",
                "REGEXP_LIKE(resolver_version, '^[A-Z0-9_]+$', 'c')");
    }

    @Test
    void migrationMustNotCreateOrMutateExecutableRuntimeObjects() throws Exception {
        String sql = Files.readString(SQL).toLowerCase();
        assertThat(sql).doesNotContain("insert into workflow_task",
                "insert into workflow_candidate_pool", "insert into workflow_task_claim",
                "alter table workflow_instance", "update role_runtime_activation");
    }

    @Test
    void hardenedGuardMustRunAllPhasesBeforePermanentDdlAndNeverRepairHistory() throws Exception {
        String sql = Files.readString(SQL);
        int phaseA = sql.indexOf("'PHASE_A_REQUEST'");
        int phaseB = sql.indexOf("'PHASE_B_COMPLETENESS'");
        int phaseC = sql.indexOf("'PHASE_C_INTEGRITY'");
        int phaseD = sql.indexOf("'PHASE_D_COMPLETE'");
        int firstDdl = sql.indexOf("CREATE TABLE workflow_role_binding_promotion");
        assertThat(phaseA).isGreaterThanOrEqualTo(0).isLessThan(phaseB);
        assertThat(phaseB).isLessThan(phaseC);
        assertThat(phaseC).isLessThan(phaseD);
        assertThat(phaseD).isLessThan(firstDdl);

        String guard = sql.substring(0, firstDdl).toLowerCase();
        assertThat(guard).doesNotContain("update role_runtime_activation",
                "delete from role_runtime_activation", "insert into role_runtime_activation");
    }

    @Test
    void hardenedGuardMustCoverTheFrozenElevenScenarioMatrix() throws Exception {
        String sql = Files.readString(SQL);
        int firstDdl = sql.indexOf("CREATE TABLE workflow_role_binding_promotion");
        String guard = sql.substring(0, firstDdl);
        Map<String, String> scenarios = new LinkedHashMap<>();
        scenarios.put("missing approval", "COUNT(*) FROM role_runtime_activation_approval");
        scenarios.put("approval exists but evidence missing", "COUNT(*) FROM role_runtime_activation_evidence");
        scenarios.put("cross activation approval", "r.activation_id = a.activation_id");
        scenarios.put("evidence references wrong approval", "a.id = e.approval_id");
        scenarios.put("approval is not approved", "a.decision <> 'APPROVE'");
        scenarios.put("activation hash drift", "e.activation_hash <> r.activation_hash");
        scenarios.put("contract hash drift", "e.contract_hash <> r.contract_hash");
        scenarios.put("binding hash drift", "e.binding_hash <> r.binding_hash");
        scenarios.put("candidate hash drift", "e.evidence_hash = r.candidate_hash");
        scenarios.put("directory revision and evidence drift", "directory_revision < 0");
        scenarios.put("complete chain reaches phase D", "VALUES ('PHASE_D_COMPLETE', 0)");
        scenarios.forEach((scenario, contract) -> assertThat(guard)
                .as(scenario).contains(contract));
        assertThat(guard).contains("<> 3", "<> 5", "COUNT(DISTINCT a.approver_type)",
                "COUNT(DISTINCT e.evidence_type)", "e.evidence_hash = r.directory_contract_hash");
    }

    @Test
    void hardeningMustLeaveTheThreeTableDdlByteSemanticsUnchanged() throws Exception {
        String hardened = normalizedDdlSuffix(Files.readString(SQL));
        String failedCandidate = normalizedDdlSuffix(Files.readString(FAILED_CANDIDATE));
        assertThat(hardened).isEqualTo(failedCandidate);
    }

    @Test
    void repositoriesMustExposeOnlyInsertAndQueryOperations() {
        Set<String> methods = Set.of(RoleRuntimeBindingPromotionRepository.class,
                RoleRuntimeBindingCandidateSnapshotRepository.class,
                RoleRuntimeBindingLifecycleRepository.class).stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(Method::getName).collect(Collectors.toSet());
        assertThat(methods).contains("insert", "append");
        assertThat(methods).noneMatch(name -> name.startsWith("update")
                || name.startsWith("delete") || name.startsWith("saveOrUpdate"));
    }

    @Test
    void internalPersistenceBoundaryMustBeTransactionalAndExposeNoController() throws Exception {
        Method persist = Arrays.stream(RoleRuntimeBindingPersistenceApplicationService.class
                        .getDeclaredMethods())
                .filter(method -> method.getName().equals("persist"))
                .findFirst().orElseThrow();
        assertThat(persist.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(RoleRuntimeBindingPersistenceApplicationService.class.getAnnotations())
                .extracting(annotation -> annotation.annotationType().getSimpleName())
                .doesNotContain("RestController", "Controller");
    }

    private static int count(String value, String needle) {
        int count = 0;
        for (int at = value.indexOf(needle); at >= 0; at = value.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    private static String normalizedDdlSuffix(String sql) {
        String marker = "CREATE TABLE workflow_role_binding_promotion";
        return sql.substring(sql.indexOf(marker)).replace("\r\n", "\n").stripTrailing();
    }
}
