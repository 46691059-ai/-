package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectorySourceType;
import cn.gov.enterprise.modules.workflow.domain.sandbox.FakeRoleDirectoryAdapter;
import cn.gov.enterprise.modules.workflow.domain.sandbox.RoleRuntimeSandboxExecutor;
import cn.gov.enterprise.modules.workflow.domain.sandbox.SandboxExecutionResult;
import cn.gov.enterprise.modules.workflow.domain.sandbox.SandboxExecutionStatus;
import cn.gov.enterprise.modules.workflow.domain.sandbox.SandboxFault;
import cn.gov.enterprise.modules.workflow.domain.sandbox.SandboxRoleRuntimeContext;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimeSandboxExecutionTest {
    private static final Instant NOW = Instant.parse("2026-08-13T09:00:00Z");
    private static final String ROLE = "SANDBOX_ROLE_REVIEWER";
    private static final String ORG = "SANDBOX_ORG_DIGITAL";

    @Test
    void completeSandboxChainMustProduceOnlySyntheticHashesAndClaimSimulation() {
        SandboxExecutionResult result = executor(members(2)).execute(context(10));

        assertThat(result.executionStatus()).isEqualTo(SandboxExecutionStatus.COMPLETED);
        assertThat(result.bindingHash()).matches("[0-9a-f]{64}");
        assertThat(result.candidateHash()).matches("[0-9a-f]{64}");
        assertThat(result.activationHash()).matches("[0-9a-f]{64}");
        assertThat(result.claimSimulation().syntheticUserId())
                .startsWith("SANDBOX_USER_");
        assertThat(result.auditEvidence().stepHashes().keySet()).containsExactlyInAnyOrder(
                "DIRECTORY_RESOLVED", "CANDIDATE_ADAPTED", "BINDING_GENERATED",
                "ELIGIBILITY_PASSED", "ACTIVATION_APPROVED",
                "RUNTIME_BINDING_CANDIDATE", "CANDIDATE_GENERATED", "CLAIM_SIMULATED");
    }

    @Test
    void emptyDirectoryMustFailClosed() {
        SandboxExecutionResult result = executor(List.of()).execute(context(10));
        assertThat(result.executionStatus()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.failureReason()).contains("no effective approval-role member");
    }

    @Test
    void contractAndBindingHashDriftMustFailClosed() {
        SandboxExecutionResult contract = executor(members(1)).execute(
                context(10), SandboxFault.CONTRACT_HASH_DRIFT);
        SandboxExecutionResult binding = executor(members(1)).execute(
                context(10), SandboxFault.BINDING_HASH_DRIFT);
        assertThat(contract.executionStatus()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(contract.failureReason()).contains("binding does not match");
        assertThat(binding.executionStatus()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(binding.failureReason()).isEqualTo("BINDING_HASH_MISMATCH");
    }

    @Test
    void missingActivationApprovalMustFailBeforeClaimSimulation() {
        SandboxExecutionResult result = executor(members(1)).execute(
                context(10), SandboxFault.ACTIVATION_NOT_APPROVED);
        assertThat(result.executionStatus()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("ACTIVATION_APPROVAL_MISSING");
        assertThat(result.claimSimulation()).isNull();
    }

    @Test
    void candidateLimitMustRejectWithoutTruncation() {
        SandboxExecutionResult result = executor(members(2)).execute(context(1));
        assertThat(result.executionStatus()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.failureReason()).contains("candidate limit exceeded");
    }

    @Test
    void sameInputMustProduceStableBindingCandidateAndActivationHashes() {
        SandboxExecutionResult left = executor(members(2)).execute(context(10));
        SandboxExecutionResult right = executor(members(2)).execute(context(10));
        assertThat(left.bindingHash()).isEqualTo(right.bindingHash());
        assertThat(left.candidateHash()).isEqualTo(right.candidateHash());
        assertThat(left.activationHash()).isEqualTo(right.activationHash());
        assertThat(left.auditEvidence().stepHashes().get("ELIGIBILITY_PASSED"))
                .isEqualTo(right.auditEvidence().stepHashes().get("ELIGIBILITY_PASSED"));

        SandboxExecutionResult changed = executor(members(1)).execute(context(10));
        assertThat(changed.candidateHash()).isNotEqualTo(left.candidateHash());
        assertThat(changed.activationHash()).isNotEqualTo(left.activationHash());
    }

    @Test
    void fakeDirectoryMustRejectNonSandboxData() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new FakeRoleDirectoryAdapter("REAL_ROLE", ORG, NOW, 1, members(1)))
                .isInstanceOf(IllegalArgumentException.class);
        RoleDirectoryMember realMember = new RoleDirectoryMember(
                "REAL_USER", "SANDBOX_ASSIGNMENT_1", ROLE, ORG,
                NOW.minusSeconds(60), null, RoleDirectorySourceType.MANUAL_GOVERNANCE,
                "SANDBOX_SOURCE_1", 1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new FakeRoleDirectoryAdapter(ROLE, ORG, NOW, 1, List.of(realMember)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explicitUserMustRemainActiveAndRoleResolverNonExecutable() {
        ExplicitUserResolver explicit = new ExplicitUserResolver();
        ResolverRegistry registry = new ResolverRegistry(List.of(explicit.descriptor(),
                RoleDirectoryResolver.PREPARED_DESCRIPTOR), List.of(explicit));
        assertThat(registry.require(ExplicitUserResolver.CODE).descriptor().status())
                .isEqualTo(ResolverStatus.ACTIVE);
        assertThat(registry.requireDescriptor(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION).status()).isEqualTo(ResolverStatus.PREPARED);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registry.require(
                RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                RoleDirectoryResolver.CONTRACT_HASH)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void sandboxMustHaveNoProductionRuntimeOrPersistenceDependencies() {
        List<String> fieldTypes = Arrays.stream(RoleRuntimeSandboxExecutor.class
                .getDeclaredFields()).map(Field::getType).map(Class::getName).toList();
        assertThat(fieldTypes).containsExactly(FakeRoleDirectoryAdapter.class.getName());
        assertThat(fieldTypes).noneMatch(name -> name.contains("Repository")
                || name.contains("WorkflowInstance") || name.contains("WorkflowTask")
                || name.contains("CandidatePool") || name.contains("TaskClaim")
                || name.startsWith("org.springframework") || name.startsWith("com.baomidou"));
    }

    private static RoleRuntimeSandboxExecutor executor(List<RoleDirectoryMember> members) {
        return new RoleRuntimeSandboxExecutor(
                new FakeRoleDirectoryAdapter(ROLE, ORG, NOW, 1, members));
    }

    private static SandboxRoleRuntimeContext context(int limit) {
        return new SandboxRoleRuntimeContext("SANDBOX-WF59", ROLE, ORG, NOW,
                RoleDirectoryResolver.VERSION, RoleDirectoryResolver.CONTRACT_HASH, limit);
    }

    private static List<RoleDirectoryMember> members(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> new RoleDirectoryMember(
                        "SANDBOX_USER_" + index,
                        "SANDBOX_ASSIGNMENT_" + index,
                        ROLE, ORG, NOW.minusSeconds(60), null,
                        RoleDirectorySourceType.MANUAL_GOVERNANCE,
                        "SANDBOX_SOURCE_" + index, 1))
                .toList();
    }
}
