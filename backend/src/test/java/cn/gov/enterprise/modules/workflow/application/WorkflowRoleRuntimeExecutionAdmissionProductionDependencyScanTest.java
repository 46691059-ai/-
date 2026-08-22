package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimeExecutionAdmissionProductionDependencyScanTest {
    private static final Path ADMISSION_DOMAIN = Path.of(
            "src/main/java/cn/gov/enterprise/modules/workflow/domain/role/admission");
    private static final Path ADMISSION_SERVICE = Path.of(
            "src/main/java/cn/gov/enterprise/modules/workflow/application/service/"
                    + "RoleRuntimeExecutionAdmissionApplicationService.java");
    private static final List<String> FORBIDDEN = List.of(
            "cn.gov.enterprise.modules.investment",
            "ProductionRoleDirectoryAdapter",
            "org.springframework",
            "com.baomidou",
            "interfaces.rest",
            "WorkflowInstanceRepository",
            "WorkflowNodeExecutionRepository",
            "WorkflowTaskRepository",
            "CandidatePoolRepository",
            "CandidatePoolApplicationService",
            "TaskClaimApplicationService",
            "TaskClaimRepository");

    @Test
    void admissionFrameworkMustNotDependOnProductionRuntimeOrWriteServices() throws Exception {
        List<Path> sources;
        try (var domainFiles = Files.walk(ADMISSION_DOMAIN)) {
            sources = domainFiles.filter(path -> path.toString().endsWith(".java")).toList();
        }
        sources = new java.util.ArrayList<>(sources);
        sources.add(ADMISSION_SERVICE);

        List<String> violations = sources.stream().flatMap(path -> {
            try {
                String source = Files.readString(path);
                return FORBIDDEN.stream().filter(source::contains)
                        .map(token -> path + " -> " + token);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }).toList();
        assertThat(violations).isEmpty();
    }

    @Test
    void noControllerMigrationOrHistoricalAssetDriftMayBeIntroduced() throws Exception {
        Path controllers = Path.of(
                "src/main/java/cn/gov/enterprise/modules/workflow/interfaces/rest");
        try (var files = Files.list(controllers)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.contains("Admission"));
        }

        Path migrationRoot = Path.of("../database/migration/mysql");
        try (var files = Files.list(migrationRoot)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .filteredOn(name -> name.startsWith("V2.6.15__"))
                    .containsExactly("V2.6.15__create_role_runtime_execution_admission_persistence.sql");
        }
        Path v2614 = migrationRoot.resolve(
                "V2.6.14__create_role_runtime_binding_persistence_foundation.sql");
        assertThat(sha256(v2614)).isEqualTo(
                "a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442");
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}
