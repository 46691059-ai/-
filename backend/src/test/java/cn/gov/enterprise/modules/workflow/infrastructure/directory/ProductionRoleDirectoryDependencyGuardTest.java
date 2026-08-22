package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionRoleDirectoryDependencyGuardTest {
    @Test
    void productionAdapterSourceMustNotDependOnRuntimeOrBusinessPersistence() throws Exception {
        Path source = Path.of("src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/directory/ProductionRoleDirectoryAdapter.java");
        String text = Files.readString(source);
        assertThat(text).doesNotContain("modules.investment", "TaskService", "CandidatePoolService",
                "ClaimService", "SysUserRoleMapper", "HrMapper", "OrganizationMapper",
                "@Component", "@Service", "@Primary");
    }

    @Test
    void auditEvidenceModelHasNoSensitivePiiFields() {
        assertThat(List.of(DirectoryResolutionAuditEvidence.class.getRecordComponents()).stream()
                .map(component -> component.getName()).toList())
                .doesNotContain("phone", "mobile", "identityCard", "address", "name");
    }
}
