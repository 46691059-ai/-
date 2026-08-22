package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.application.service.RoleRuntimeExecutionAdmissionPersistenceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimeExecutionAdmissionPersistenceIsolationTest {
    @Test void persistenceFoundationMustNotDependOnProductionOrRuntimeCreationPorts() throws Exception {
        List<Path> roots=List.of(
                Path.of("src/main/java/cn/gov/enterprise/modules/workflow/domain/role/admission/persistence"),
                Path.of("src/main/java/cn/gov/enterprise/modules/workflow/application/service/RoleRuntimeExecutionAdmissionPersistenceService.java"),
                Path.of("src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/RoleRuntimeExecutionAdmissionEntityMapper.java"));
        List<String> forbidden=List.of("modules.investment","ProductionRoleDirectory","DataScopeCapabilityAdapter","SoDCapabilityAdapter",
                "FeatureFlagCapabilityAdapter","KillSwitchCapabilityAdapter","WorkflowInstanceRepository","WorkflowTaskRepository",
                "CandidatePoolRepository","TaskClaimRepository","interfaces.rest");
        var violations=new java.util.ArrayList<String>(); for(Path root:roots){List<Path> files=Files.isDirectory(root)?Files.walk(root).filter(p->p.toString().endsWith(".java")).toList():List.of(root);for(Path file:files){String source=Files.readString(file);for(String token:forbidden)if(source.contains(token))violations.add(file+" -> "+token);}}
        assertThat(violations).isEmpty();
        assertThat(RoleRuntimeExecutionAdmissionPersistenceService.class.getAnnotations())
                .extracting(a->a.annotationType().getSimpleName()).doesNotContain("Controller","RestController");
    }
}
