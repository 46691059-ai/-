package cn.gov.enterprise.modules.organization.approvalrole;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V2620ApprovalRoleDirectoryProviderAuditMigrationContractTest {
    @Test void migrationMustFreezeDurableAppendOnlyPiiMinimizedLedger()throws Exception{
        String sql=Files.readString(Path.of("..","database","migration","mysql","V2.6.20__create_approval_role_directory_provider_audit.sql"));
        assertThat(sql).contains("CREATE TABLE approval_role_directory_provider_audit","audit_event_id","request_hash","evidence_hash",
                "outcome IN ('SUCCESS','REJECTED','FAILED')","APPROVAL_ROLE_DIRECTORY_PROVIDER_AUDIT_APPEND_ONLY",
                "BEFORE UPDATE","BEFORE DELETE","NOT_YET_PRODUCTION_APPROVED");
        assertThat(sql.toLowerCase()).doesNotContain("authorization_header","service_token","private_key","id_card","phone","member_payload");
    }
}
