package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.application.service.RoleRuntimeExecutionAdmissionPersistenceService;
import cn.gov.enterprise.modules.workflow.domain.repository.*;
import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.RoleRuntimeExecutionAdmissionValidatorContract;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class V2615ExecutionAdmissionPersistenceMigrationContractTest {
    private static final Path SQL=Path.of("..","database","migration","mysql","V2.6.15__create_role_runtime_execution_admission_persistence.sql");

    @Test void guardMustPrecedeEveryPermanentDdlAndNeverRepairHistory() throws Exception {
        String sql=Files.readString(SQL); int guard=sql.indexOf("CREATE TEMPORARY TABLE tmp_role_admission_v2615_guard");
        int alter=sql.indexOf("ALTER TABLE workflow_role_binding_candidate_snapshot");
        assertThat(guard).isGreaterThanOrEqualTo(0).isLessThan(alter);
        String prefix=sql.substring(0,alter).toLowerCase();
        assertThat(prefix).doesNotContain("update workflow_role_binding", "delete from workflow_role_binding", "insert into workflow_role_binding");
        assertThat(sql).contains("Legacy rows intentionally remain NULL").doesNotContain("SET directory_result_hash");
    }

    @Test void migrationMustCreateOptionCAndCasSlotWithStrongOwnership() throws Exception {
        String sql=Files.readString(SQL);
        assertThat(sql).contains("CREATE TABLE workflow_role_runtime_execution_admission (",
                "CREATE TABLE workflow_role_runtime_execution_admission_evidence (",
                "CREATE TABLE workflow_role_runtime_execution_admission_event (",
                "CREATE TABLE workflow_role_runtime_execution_admission_slot (",
                "fk_role_admission_candidate","fk_role_admission_definition_version",
                "fk_role_admission_node","fk_role_admission_release","uk_role_admission_idempotency");
        assertThat(sql).contains("directory_result_hash","directory_fence_token_hash","definition_release_id",
                "definition_version_id","node_binding_hash","graph_hash","CHARACTER SET ascii COLLATE ascii_bin");
        assertThat(sql).contains("REGEXP_LIKE(resolver_version,'^[A-Z0-9_]+$','c')");
        assertThat(sql).contains("ADD UNIQUE KEY uk_role_binding_snapshot_slot_owner (id,snapshot_id,delete_token)",
                "tmp_role_admission_v2615_parent_key_proof", "CANDIDATE_SLOT_OWNER");
        assertThat(sql.indexOf("CREATE TABLE workflow_role_runtime_execution_admission_slot"))
                .isLessThan(sql.indexOf("CREATE TABLE workflow_role_runtime_execution_admission_evidence"));
        assertThat(sql.indexOf("tmp_role_admission_v2615_parent_key_proof"))
                .isLessThan(sql.indexOf("ADD CONSTRAINT fk_role_admission_slot_candidate"));
    }

    @Test void partialInstallationMustFailClosedBeforePermanentDdl() throws Exception {
        String sql=Files.readString(SQL);
        String guard=sql.substring(0,sql.indexOf("ALTER TABLE workflow_role_binding_candidate_snapshot"));
        assertThat(guard).contains("NO_PARTIAL_INSTALL", "uk_role_binding_snapshot_slot_owner",
                "workflow_role_runtime_execution_admission",
                "workflow_role_runtime_execution_admission_evidence",
                "workflow_role_runtime_execution_admission_event",
                "workflow_role_runtime_execution_admission_slot",
                "information_schema.columns", "information_schema.statistics",
                "information_schema.table_constraints", "information_schema.triggers");
        assertThat(guard.toLowerCase()).doesNotContain(" drop table ", " drop trigger ",
                " update workflow_role_", " delete from workflow_role_", " insert into workflow_role_");
    }

    @Test void appendOnlyAndIsolationContractsMustBeExplicit() throws Exception {
        String sql=Files.readString(SQL);
        assertThat(count(sql,"CREATE TRIGGER trg_role_admission_no_update")
                + count(sql,"CREATE TRIGGER trg_role_admission_evidence_no_update")
                + count(sql,"CREATE TRIGGER trg_role_admission_event_no_update")).isEqualTo(3);
        assertThat(count(sql,"CREATE TRIGGER trg_role_admission_no_delete")
                + count(sql,"CREATE TRIGGER trg_role_admission_evidence_no_delete")
                + count(sql,"CREATE TRIGGER trg_role_admission_event_no_delete")).isEqualTo(3);
        assertThat(sql).contains("ROLE_EXECUTION_ADMISSION_APPEND_ONLY","ROLE_EXECUTION_ADMISSION_EVIDENCE_APPEND_ONLY",
                "ROLE_EXECUTION_ADMISSION_EVENT_APPEND_ONLY","ROLE_ADMISSION_EVENT_CHAIN_INVALID",
                "CREATE TRIGGER trg_role_admission_slot_no_delete",
                "ROLE_RUNTIME_ADMISSION_SLOT_DELETE_FORBIDDEN", "ROLE_ADMISSION_SLOT_CAS_ONLY");
        String lower=sql.toLowerCase(); assertThat(lower).doesNotContain("insert into workflow_instance","insert into workflow_task",
                "insert into workflow_candidate_pool","insert into workflow_task_claim","update workflow_instance");
    }

    @Test void finalApprovalGuardMustFreezeExactValidatorCapabilityHashAndTerminalContracts() throws Exception {
        String sql=Files.readString(SQL);
        for(String code:RoleRuntimeExecutionAdmissionValidatorContract.VALIDATOR_CODES) {
            assertThat(sql).contains("validator_code='"+code+"'");
        }
        for(String capability:RoleRuntimeExecutionAdmissionValidatorContract.REQUIRED_CAPABILITIES.values()) {
            assertThat(sql).contains("capability_code='"+capability+"'");
        }
        assertThat(sql).contains("evidence_count<>28", "distinct_orders<>28", "distinct_codes<>28",
                "capability_count<>8", "capability_not_ready<>0",
                "ROLE_ADMISSION_EVIDENCE_INCOMPLETE", "ROLE_ADMISSION_CAPABILITY_NOT_READY",
                "ROLE_RUNTIME_CAPABILITY_EVIDENCE_ROOT_CANONICAL_V1", "SET actual_root=SHA2",
                "NOT (NEW.source_evidence_root_hash<=>actual_root)", "NOT (NEW.source_persistence_hash<=>stored_persistence)",
                "ROLE_ADMISSION_PERSISTENCE_HASH_MISMATCH", "active_token", "NEW.version<>OLD.version+1",
                "uk_role_admission_event_decision_terminal", "uk_role_admission_event_closure_terminal");
    }

    @Test void repositoriesExposeOnlyAppendQueryAndCasAndServiceIsTransactional() throws Exception {
        Set<String> methods=Set.of(RoleRuntimeExecutionAdmissionRepository.class,
                RoleRuntimeExecutionAdmissionEvidenceRepository.class,RoleRuntimeExecutionAdmissionEventRepository.class,
                RoleRuntimeExecutionAdmissionSlotRepository.class).stream().flatMap(t->Arrays.stream(t.getDeclaredMethods()))
                .map(Method::getName).collect(Collectors.toSet());
        assertThat(methods).noneMatch(n->n.startsWith("updateAdmission")||n.startsWith("delete")||n.equals("saveOrUpdate")||n.equals("replace"));
        Method persist=RoleRuntimeExecutionAdmissionPersistenceService.class.getDeclaredMethod("persist",
                cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.RoleRuntimeExecutionAdmissionPersistenceBundle.class,
                java.time.Instant.class);
        assertThat(persist.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static int count(String value,String needle){int count=0;for(int at=value.indexOf(needle);at>=0;at=value.indexOf(needle,at+needle.length()))count++;return count;}
}
