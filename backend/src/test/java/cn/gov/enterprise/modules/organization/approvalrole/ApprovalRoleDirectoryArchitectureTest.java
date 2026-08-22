package cn.gov.enterprise.modules.organization.approvalrole;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class ApprovalRoleDirectoryArchitectureTest {
    @Test void organizationDomainHasNoFrameworkWorkflowInvestmentOrPersistenceDependency() throws Exception {
        Path root=Path.of("src/main/java/cn/gov/enterprise/modules/organization/approvalrole/domain");
        try(var files=Files.walk(root)){for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()){
            String source=Files.readString(file);assertThat(source).doesNotContain("org.springframework","com.baomidou","modules.workflow","modules.investment","infrastructure.persistence");
        }}
    }
    @Test void onlyTheInternalProviderControllerIsExposed() throws Exception {
        Path root=Path.of("src/main/java/cn/gov/enterprise/modules/organization/approvalrole");
        try(var files=Files.walk(root)){
            assertThat(files.filter(p->p.getFileName().toString().endsWith("Controller.java")).toList())
                    .singleElement()
                    .satisfies(path -> assertThat(path.toString().replace('\\', '/'))
                            .endsWith("approvalrole/interfaces/internal/ApprovalRoleDirectoryProviderController.java"));
        }
    }
}
