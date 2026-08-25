package cn.gov.enterprise.modules.workflow.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.organization.approvalrole.application.ApprovalRoleCommandService;
import cn.gov.enterprise.modules.organization.approvalrole.application.ApprovalRoleDirectoryService;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleAssignmentSource;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleAssignmentSourceType;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleCanonical;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleCode;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryQuery;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleIdentityGenerator;
import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowDefinitionCommand;
import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.command.PublishWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.command.ReplaceWorkflowNodesCommand;
import cn.gov.enterprise.modules.workflow.application.service.VersionResolverBindingApplicationService;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowDefinitionApplicationService;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBindingCanonical;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TEST_ONLY opt-in fixture entry point. It is unreachable in normal tests and production startup.
 * The PowerShell guard must authorize an isolated RC2 TEST database before this class can write.
 */
@SpringBootTest
@Import(Rc2ControlledCanaryFixtureSeederTest.DeterministicFixtureIds.class)
@EnabledIfSystemProperty(named = "rc2.fixture.execute", matches = "true")
class Rc2ControlledCanaryFixtureSeederTest {
    private static final long ENTERPRISE_ID = 990001L;
    private static final long ORG_ID = 990101L;
    private static final long DEFINITION_ID = 990401L;
    private static final long VERSION_ID = 990402L;
    private static final long NODE_ID = 990404L;
    private static final long BINDING_ID = 990405L;
    private static final String ROLE_CODE = "RC1_TEST_CANARY_APPROVER";

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApprovalRoleCommandService approvalRoles;
    @Autowired ApprovalRoleDirectoryService directory;
    @Autowired WorkflowDefinitionApplicationService definitions;
    @Autowired VersionResolverBindingApplicationService bindings;
    @Autowired ResolverRegistry resolverRegistry;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> requiredEnvironment("RC2_FIXTURE_DB_URL"));
        properties.add("spring.datasource.username", () -> requiredEnvironment("RC2_FIXTURE_DB_USERNAME"));
        properties.add("spring.datasource.password", () -> requiredEnvironment("RC2_FIXTURE_DB_PASSWORD"));
        properties.add("app.security.jwt.secret", () -> requiredEnvironment("JWT_SECRET"));
        properties.add("app.database.mapping-check.enabled", () -> "false");
        properties.add("workflow.role-runtime.claim-enabled", () -> "false");
        properties.add("workflow.role-runtime.kill-switch", () -> "STOP_NEW_AND_CLAIM");
        properties.add("app.approval-role-directory.provider.enabled", () -> "false");
        properties.add("app.workflow-external-audit.enabled", () -> "false");
    }

    @Test
    void executeGovernedFixtureAction() {
        requireAttestationAuthorization();
        installSecurityPrincipal();
        String action = System.getProperty("rc2.fixture.action", "");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        if ("seed".equals(action)) {
            transaction.executeWithoutResult(ignored -> seed());
        } else if ("decommission".equals(action)) {
            transaction.executeWithoutResult(ignored -> decommission());
        } else {
            throw new IllegalStateException("REFUSE_TO_EXECUTE: unsupported fixture action");
        }
    }

    private void seed() {
        assertTargetAbsent();
        Path repository = Path.of("").toAbsolutePath().normalize().getParent();
        Path identity = repository.resolve("database/test-fixtures/rc2/01_rc2_canary_identity.sql");
        new ResourceDatabasePopulator(new FileSystemResource(identity)).execute(dataSource);

        approvalRoles.createRole(new ApprovalRoleCommandService.CreateRole(
                Long.toString(ENTERPRISE_ID), ROLE_CODE, "RC2 TEST Canary Approver",
                "TEST_ONLY controlled canary role", "RC2_TEST_FIXTURE"));
        approvalRoles.assignUsers(new ApprovalRoleCommandService.BatchAssignUsers(
                Long.toString(ENTERPRISE_ID), ORG_ID, ROLE_CODE,
                List.of(user(990201L, "RC2_TEST_CANARY_ASSIGNMENT_01"),
                        user(990202L, "RC2_TEST_CANARY_ASSIGNMENT_02")),
                "RC2_TEST_FIXTURE"));
        var directoryResult = directory.resolve(new ApprovalRoleDirectoryQuery(
                Long.toString(ENTERPRISE_ID), ORG_ID, new ApprovalRoleCode(ROLE_CODE),
                Rc2ControlledCanaryFixtureContract.DIRECTORY_EFFECTIVE_AT));
        Rc2ControlledCanaryFixtureContract.requireUnified(
                directoryResult.effectiveAt(),
                Rc2ControlledCanaryFixtureContract.DIRECTORY_EFFECTIVE_AT);
        assertThat(directoryResult.revision()).isEqualTo(1L);
        assertThat(directoryResult.members()).extracting(member -> member.userId())
                .containsExactly(990201L, 990202L);
        assertThat(directoryResult.resultHash())
                .isEqualTo(ApprovalRoleCanonical.resultHash(directoryResult));
        assertThat(jdbc.queryForObject("SELECT current_result_hash FROM approval_role_revision_head "
                + "WHERE id=990302", String.class)).isEqualTo(directoryResult.resultHash());

        var definition = definitions.createDefinition(new CreateWorkflowDefinitionCommand(
                "RC2_TEST_CANARY_ROLE_APPROVAL", "RC2 TEST Canary Role Approval",
                "RC2_TEST_CANARY", ENTERPRISE_ID, ORG_ID,
                "TEST_ONLY; no runtime activation"));
        assertThat(definition.id()).isEqualTo(DEFINITION_ID);
        var version = definitions.createVersion(DEFINITION_ID,
                new CreateWorkflowVersionCommand("1.0", "RC2 TEST fixture version", null));
        assertThat(version.id()).isEqualTo(VERSION_ID);
        assertThat(jdbc.update("UPDATE workflow_version SET resolver_binding_model=?, "
                        + "updated_by='RC2_TEST_FIXTURE', updated_time=CURRENT_TIMESTAMP(3), "
                        + "version=version+1 WHERE id=? AND status='DRAFT' AND version=0",
                "VERSION_RESOLVER_BINDING_CAPABLE", VERSION_ID)).isEqualTo(1);

        var nodes = definitions.replaceNodes(DEFINITION_ID, VERSION_ID,
                new ReplaceWorkflowNodesCommand(List.of(new ReplaceWorkflowNodesCommand.NodeCommand(
                        "RC2_TEST_ROLE_APPROVAL", "RC2 TEST Role Approval",
                        WorkflowNode.NodeType.APPROVAL, 1,
                        WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                        WorkflowNode.ApprovalMode.SINGLE, null,
                        WorkflowNode.AssignmentRuleType.RULE,
                        "{\"organizationId\":990101,\"resolverCode\":\"ROLE_DIRECTORY\","
                                + "\"roleCode\":\"RC1_TEST_CANARY_APPROVER\"}",
                        null, null, null, false, true))));
        assertThat(nodes).singleElement().extracting(WorkflowNode::id).isEqualTo(NODE_ID);

        var descriptor = resolverRegistry.requireDescriptor(
                ResolverCode.of("ROLE_DIRECTORY"), ResolverVersion.of("ROLE_DIRECTORY_V1"));
        VersionNodeResolverBinding draft = new VersionNodeResolverBinding(
                BINDING_ID, DEFINITION_ID, VERSION_ID, NODE_ID, 1,
                descriptor.code(), descriptor.version(), descriptor.contractHash(),
                AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL,
                AssignmentStrategy.Type.ROLE, ROLE_CODE, OrganizationScopeType.FIXED_ORG,
                ORG_ID, EffectiveTimePolicy.NODE_ACTIVATED_AT,
                VersionNodeResolverBinding.SCHEMA_VERSION, "0".repeat(64), 0);
        String bindingHash = VersionNodeResolverBindingCanonical.compute(draft).bindingHash();
        bindings.add(draft.withBindingHash(bindingHash));

        var published = definitions.publishVersion(DEFINITION_ID, VERSION_ID,
                new PublishWorkflowVersionCommand(0, 1,
                        "RC2 TEST controlled canary fixture publication"));
        assertThat(published.version().status().name()).isEqualTo("PUBLISHED");
        assertThat(published.version().resolverBindingManifestHash()).matches("[0-9a-f]{64}");
        assertPostSeedState(directoryResult.resultHash(), bindingHash);
    }

    private ApprovalRoleCommandService.UserAssignment user(long userId, String reference) {
        ApprovalRoleAssignmentSource source = new ApprovalRoleAssignmentSource(
                ApprovalRoleAssignmentSourceType.GOVERNANCE_DECISION,
                "RC2_TEST_FIXTURE", reference, ApprovalRoleCanonical.sha256(reference),
                100, Rc2ControlledCanaryFixtureContract.DIRECTORY_EFFECTIVE_AT);
        return new ApprovalRoleCommandService.UserAssignment(userId,
                Rc2ControlledCanaryFixtureContract.DIRECTORY_EFFECTIVE_AT,
                Rc2ControlledCanaryFixtureContract.ASSIGNMENT_EFFECTIVE_TO, source);
    }

    private void decommission() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_definition WHERE id=990401 "
                + "AND status='ACTIVE'", Integer.class)).isEqualTo(1);
        Instant endedAt = Instant.now();
        approvalRoles.endAssignment(990303L, endedAt,
                "RC2 TEST governed decommission", "RC2_TEST_FIXTURE");
        approvalRoles.endAssignment(990304L, endedAt,
                "RC2 TEST governed decommission", "RC2_TEST_FIXTURE");
        approvalRoles.deactivateRole(Long.toString(ENTERPRISE_ID), ROLE_CODE, "RC2_TEST_FIXTURE");
        assertThat(jdbc.update("UPDATE workflow_version SET status='RETIRED', "
                + "effective_to=CURRENT_TIMESTAMP(3), updated_by='RC2_TEST_FIXTURE', "
                + "updated_time=CURRENT_TIMESTAMP(3), version=version+1 "
                + "WHERE id=990402 AND status='PUBLISHED' AND deleted=0")).isEqualTo(1);
        assertThat(jdbc.update("UPDATE workflow_definition SET status='ARCHIVED', "
                + "updated_by='RC2_TEST_FIXTURE', updated_time=CURRENT_TIMESTAMP(3), "
                + "version=version+1 WHERE id=990401 AND status='ACTIVE' AND deleted=0")).isEqualTo(1);
        assertThat(jdbc.update("UPDATE sys_user SET status=0, token_version=token_version+1, "
                + "update_by='RC2_TEST_FIXTURE', update_time=CURRENT_TIMESTAMP(3), version=version+1 "
                + "WHERE id IN (990201,990202) AND status=1 AND deleted=0")).isEqualTo(2);
        assertThat(jdbc.update("UPDATE sys_org SET status=0, update_by='RC2_TEST_FIXTURE', "
                + "update_time=CURRENT_TIMESTAMP(3), version=version+1 "
                + "WHERE id=990101 AND status=1 AND deleted=0")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_role_revision WHERE "
                + "enterprise_id='990001' AND role_code='RC1_TEST_CANARY_APPROVER'", Integer.class))
                .isGreaterThanOrEqualTo(4);
    }

    private void assertTargetAbsent() {
        Integer count = jdbc.queryForObject("SELECT "
                + "(SELECT COUNT(*) FROM sys_org WHERE id=990101 OR org_code='RC2_TEST_CANARY_ORG')+"
                + "(SELECT COUNT(*) FROM sys_user WHERE id IN (990201,990202) OR username LIKE 'RC2_TEST_CANARY_USER_%')+"
                + "(SELECT COUNT(*) FROM approval_role WHERE id=990301 OR role_code='RC1_TEST_CANARY_APPROVER')+"
                + "(SELECT COUNT(*) FROM workflow_definition WHERE id=990401 OR definition_code='RC2_TEST_CANARY_ROLE_APPROVAL')",
                Integer.class);
        assertThat(count).as("fixture must be completely absent").isZero();
    }

    private void assertPostSeedState(String directoryHash, String bindingHash) {
        assertThat(jdbc.queryForObject("SELECT result_hash FROM approval_role_revision WHERE id=990305",
                String.class)).isEqualTo(directoryHash);
        assertThat(jdbc.queryForObject("SELECT binding_hash FROM workflow_version_node_resolver_binding "
                + "WHERE id=990405", String.class)).isEqualTo(bindingHash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_version_resolver_binding_manifest "
                + "WHERE id=990406 AND binding_count=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_version_release WHERE id=990407",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_instance WHERE definition_id=990401",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_role_runtime_governance_control "
                + "WHERE enterprise_id='990001'", Integer.class)).isZero();
    }

    private void requireAttestationAuthorization() {
        if (!"RC2_TEST_FIXTURE_EXECUTION".equals(
                System.getenv("RC2_FIXTURE_EXECUTION_AUTHORIZED"))) {
            throw new IllegalStateException("REFUSE_TO_EXECUTE: fixture execution authorization missing");
        }
        String attestationId = requiredEnvironment("RC2_ENVIRONMENT_ATTESTATION_ID");
        if (!attestationId.matches("^[0-9a-f]{64}$")) {
            throw new IllegalStateException("REFUSE_TO_EXECUTE: invalid Environment Attestation ID");
        }
        Path attestationResult = Path.of(requiredEnvironment(
                "RC2_ENVIRONMENT_ATTESTATION_RESULT_PATH")).toAbsolutePath().normalize();
        try {
            JsonNode result = new ObjectMapper().readTree(Files.readString(attestationResult));
            if (!"PASS".equals(result.path("result").asText())
                    || !attestationId.equals(result.path("attestationId").asText())) {
                throw new IllegalStateException(
                        "REFUSE_TO_EXECUTE: Environment Attestation evidence mismatch");
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(
                    "REFUSE_TO_EXECUTE: Environment Attestation evidence unreadable", exception);
        }
        String url = requiredEnvironment("RC2_FIXTURE_DB_URL");
        if (!url.startsWith("jdbc:mysql://127.0.0.1:") || url.contains(":34061/")) {
            throw new IllegalStateException("REFUSE_TO_EXECUTE: forbidden database target");
        }
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 "
                + "AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1", String.class))
                .isEqualTo("2.6.23");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0",
                Integer.class)).isZero();
    }

    private void installSecurityPrincipal() {
        SecurityPrincipal principal = new SecurityPrincipal(990201L,
                "RC2_TEST_FIXTURE_PUBLISHER", ORG_ID, Set.of(ORG_ID), true, false, 0);
        var authorities = List.of(
                new SimpleGrantedAuthority("workflow:definition:create"),
                new SimpleGrantedAuthority("workflow:definition:edit"),
                new SimpleGrantedAuthority("workflow:definition:publish"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("REFUSE_TO_EXECUTE: missing environment " + name);
        }
        return value;
    }

    @TestConfiguration
    static class DeterministicFixtureIds {
        @Bean @Primary WorkflowIdentityGenerator workflowFixtureIds() {
            return new QueueWorkflowIds(990401L, 990402L, 990404L, 990406L, 990407L);
        }
        @Bean @Primary ApprovalRoleIdentityGenerator approvalRoleFixtureIds() {
            if ("decommission".equals(System.getProperty("rc2.fixture.action"))) {
                return new QueueApprovalIds(990306L, 990307L, 990308L);
            }
            return new QueueApprovalIds(990301L, 990302L, 990303L, 990304L, 990305L);
        }
    }

    private static final class QueueWorkflowIds implements WorkflowIdentityGenerator {
        private final ArrayDeque<Long> ids;
        private QueueWorkflowIds(Long... ids) { this.ids = new ArrayDeque<>(List.of(ids)); }
        @Override public Long nextId() {
            if (ids.isEmpty()) throw new IllegalStateException("fixture Workflow ID sequence exhausted");
            return ids.removeFirst();
        }
    }
    private static final class QueueApprovalIds implements ApprovalRoleIdentityGenerator {
        private final ArrayDeque<Long> ids;
        private QueueApprovalIds(Long... ids) { this.ids = new ArrayDeque<>(List.of(ids)); }
        @Override public long nextId() {
            if (ids.isEmpty()) throw new IllegalStateException("fixture Directory ID sequence exhausted");
            return ids.removeFirst();
        }
    }
}
