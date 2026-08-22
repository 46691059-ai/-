package cn.gov.enterprise.modules.governance.audit;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class V2619ExternalAuditSinkMigrationContractTest {
    @Test void freezesIndependentSinkAndImmutablePayloadContract()throws Exception{
        String sql=Files.readString(Path.of("..","database","migration","mysql",
                "V2.6.19__create_governance_external_audit_sink.sql"));
        assertThat(sql).contains("governance_external_audit_event","governance_external_audit_receipt",
                "workflow_role_external_audit_payload","fk_workflow_task_claim_admission",
                "GOVERNANCE_AUDIT_EVENT_APPEND_ONLY","GOVERNANCE_AUDIT_RECEIPT_APPEND_ONLY",
                "ROLE_EXTERNAL_AUDIT_PAYLOAD_APPEND_ONLY","ascii_bin","REGEXP '^[0-9a-f]{64}$'",
                "'PLATFORM_SOD'","OLD.status='DEAD' AND NEW.status='RETRY'",
                "NEW.last_error_code='CONTROLLED_REPLAY'");
        assertThat(sql).doesNotContain("ROLE_DIRECTORY_V1','ACTIVE","ROLE_RUNTIME_ENABLED");
    }
}
