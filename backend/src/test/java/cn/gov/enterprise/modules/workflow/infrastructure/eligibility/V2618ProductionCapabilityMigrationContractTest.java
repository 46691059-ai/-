package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V2618ProductionCapabilityMigrationContractTest {
    @Test void freezes_dynamic_controls_and_external_audit_receipt_without_runtime_tables() throws Exception {
        String sql=Files.readString(Path.of("../database/migration/mysql/V2.6.18__create_role_runtime_production_capability_governance.sql"));
        assertThat(sql).contains("workflow_role_runtime_governance_control",
                "workflow_role_external_audit_outbox","workflow_role_external_audit_receipt",
                "ROLE_RUNTIME_CONTROL_APPEND_ONLY","ROLE_EXTERNAL_AUDIT_RECEIPT_APPEND_ONLY",
                "FEATURE_FLAG","CANARY","KILL_SWITCH","BUSINESS_SOD",
                "FOREIGN KEY (claim_id)","FOREIGN KEY (claim_audit_id)");
        assertThat(sql).doesNotContain("CREATE TABLE workflow_task ","CREATE TABLE workflow_candidate_pool ","UPDATE workflow_instance");
    }
}
