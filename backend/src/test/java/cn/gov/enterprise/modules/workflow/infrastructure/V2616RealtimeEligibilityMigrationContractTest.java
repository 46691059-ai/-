package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class V2616RealtimeEligibilityMigrationContractTest {
    private static final Path SQL=Path.of("..","database","migration","mysql","V2.6.16__create_role_realtime_eligibility_evidence.sql");
    @Test void freezesDatabaseEvidenceClaimContract() throws Exception {
        String sql=Files.readString(SQL);
        assertThat(sql).contains("validator_count=27","capability_count=10","ROLE_REALTIME_ELIGIBILITY_REQUIRED",
                "uk_workflow_task_claim_eligibility_evidence","fk_workflow_task_claim_eligibility_owner",
                "ROLE_REALTIME_CLAIM_BINDING_IMMUTABLE","ROLE_REALTIME_EVENT_HASH_MISMATCH");
        assertThat(sql).contains("BEFORE UPDATE ON workflow_role_realtime_eligibility_evidence",
                "BEFORE DELETE ON workflow_role_realtime_eligibility_evidence");
        assertThat(sql).doesNotContain("ROLE_RUNTIME_ENABLED");
    }
}
