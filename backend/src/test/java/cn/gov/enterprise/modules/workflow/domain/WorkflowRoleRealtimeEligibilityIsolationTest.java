package cn.gov.enterprise.modules.workflow.domain;

import cn.gov.enterprise.modules.workflow.application.service.RoleRealtimeEligibilityPreparationService;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityQuery;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRoleRealtimeEligibilityIsolationTest {
    private static final Path MAIN = Path.of("src/main/java/cn/gov/enterprise/modules/workflow");
    private static final Path DOMAIN = MAIN.resolve("domain/role/eligibility");

    @Test
    void domainHasNoFrameworkPersistenceHttpOrControllerDependency() throws IOException {
        String source = readJava(DOMAIN);
        assertThat(source).doesNotContain(
                "org.springframework", "com.baomidou", "Entity", "Controller", "HttpClient",
                "Repository", "Mapper", "@Service", "@Component");
    }

    @Test
    void productionDependencyScanProvesFrameworkCannotWriteRuntimeObjects() throws IOException {
        String domain = readJava(DOMAIN);
        String application = Files.readString(MAIN.resolve(
                "application/service/RoleRealtimeEligibilityPreparationService.java"));
        String combined = domain + application;
        assertThat(combined).doesNotContain(
                "ProductionRoleDirectoryAdapter", "modules.investment", "TaskClaimRepository",
                "WorkflowTaskRepository", "CandidatePoolRepository", "TaskClaimTransactionService",
                "WorkflowTaskController", "ClaimWorkflowTaskCommand", "System.getenv", "Secret",
                "save(", "insert(", "update(");
    }

    @Test
    void evidenceContractContainsNoSensitivePersonalProfileFields() {
        List<String> names = java.util.Arrays.stream(RealtimeEligibilityEvidence.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase()).toList();
        assertThat(names).noneMatch(name -> name.contains("phone") || name.contains("mobile")
                || name.contains("identitycard") || name.contains("address") || name.contains("salary")
                || name.contains("secret") || name.contains("password"));
    }

    @Test
    void queryCarriesFrozenReferencesNotDirectoryMemberLists() {
        var names = java.util.Arrays.stream(RealtimeEligibilityQuery.class.getRecordComponents())
                .map(component -> component.getName()).toList();
        assertThat(names).contains("candidateDirectoryRevision", "candidateDirectoryResultHash",
                "candidatePoolHash", "runtimeBindingHash");
        assertThat(names).doesNotContain("candidateUsers", "roleMembers", "secret");
    }

    @Test
    void preparationServiceIsNotRegisteredAsProductionBean() throws IOException {
        String source = Files.readString(MAIN.resolve(
                "application/service/RoleRealtimeEligibilityPreparationService.java"));
        assertThat(source).doesNotContain("@Service", "@Component", "@Bean");
        assertThat(RoleRealtimeEligibilityPreparationService.class.getAnnotations()).isEmpty();
    }

    @Test
    void directAndLegacyClaimPathsDoNotInvokeRoleRealtimeEligibilityFramework() throws IOException {
        String transactionService = Files.readString(MAIN.resolve(
                "application/service/TaskClaimTransactionService.java"));
        String applicationService = Files.readString(MAIN.resolve(
                "application/service/TaskClaimApplicationService.java"));
        assertThat(transactionService + applicationService)
                .doesNotContain("RoleRealtimeEligibilityPreparationService", "domain.role.eligibility");
    }

    private static String readJava(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            StringBuilder out = new StringBuilder();
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                out.append(Files.readString(path)).append('\n');
            }
            return out.toString();
        }
    }
}
