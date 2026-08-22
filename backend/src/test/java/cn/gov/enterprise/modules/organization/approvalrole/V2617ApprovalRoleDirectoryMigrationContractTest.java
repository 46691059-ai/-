package cn.gov.enterprise.modules.organization.approvalrole;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class V2617ApprovalRoleDirectoryMigrationContractTest {
    private static final Path SQL=Path.of("../database/migration/mysql/V2.6.17__create_approval_role_directory.sql");
    @Test void migrationContainsGovernedTablesAndConstraints() throws Exception {String s=Files.readString(SQL);assertThat(s).contains("CREATE TABLE approval_role (","CREATE TABLE approval_role_assignment (","CREATE TABLE approval_role_revision_head (","CREATE TABLE approval_role_revision (","CHARACTER SET ascii COLLATE ascii_bin","APPROVAL_ROLE_REVISION_APPEND_ONLY","APPROVAL_ROLE_ASSIGNMENT_OVERLAP","FOREIGN KEY (organization_id) REFERENCES sys_org(id)","FOREIGN KEY (user_id) REFERENCES sys_user(id)");}
    @Test void effectiveIntervalIsHalfOpenAndBetweenIsForbidden() throws Exception {String s=Files.readString(SQL);assertThat(s).contains("effective_from<effective_to","NEW.effective_from<effective_to");assertThat(s.toUpperCase()).doesNotContain(" BETWEEN ");}
    @Test void historicalCanonicalMigrationsRemainListed() throws Exception {String sums=Files.readString(Path.of("../database/migration/mysql/SHA256SUMS"));assertThat(sums).contains("db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44  V2.6.15","d5d0b154eaea4c17dc8b11d7bebda0627c9f31fd96b13043545356a5a2286084  V2.6.16");}
}
