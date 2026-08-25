package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.application.service.RoleDirectoryRuntimeBridge;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBindingSet;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.repository.NodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateAdapter;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateException;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryException;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryPort;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectorySourceType;
import cn.gov.enterprise.modules.workflow.domain.role.runtime.RoleDirectoryRuntimeTechnicalGate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class RoleDirectoryRuntimeBridgeTest {
    private static final LocalDateTime ACTIVATED =
            LocalDateTime.of(2026, 8, 24, 9, 30, 0);
    private static final Instant EFFECTIVE = ACTIVATED
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant();

    @Test
    void providerAndRuntimeBridgeRemainDisabledByDefault() {
        assertThat(RoleDirectoryResolver.PREPARED_DESCRIPTOR.status())
                .isEqualTo(ResolverStatus.PREPARED);
        assertThat(RoleDirectoryResolver.PREPARED_DESCRIPTOR.enabled()).isFalse();
        assertThat(RoleDirectoryRuntimeBridge.class.isAnnotationPresent(Component.class))
                .isFalse();
        assertThat(RoleDirectoryRuntimeBridge.class.isAnnotationPresent(Service.class))
                .isFalse();
    }

    @Test
    void buildsResolverContextFromFrozenSnapshotAtPersistedActivationTime() {
        Harness harness = harness(() -> { }, validDirectory());
        var prepared = harness.bridge().prepare(100L, 101L);

        assertThat(prepared.context().enterpriseId()).isEqualTo(10L);
        assertThat(prepared.context().definitionId()).isEqualTo(1L);
        assertThat(prepared.context().definitionVersionId()).isEqualTo(20L);
        assertThat(prepared.context().roleCode()).isEqualTo("ROLE_APPROVER");
        assertThat(prepared.context().resolvedOrganizationId()).isEqualTo(8L);
        assertThat(prepared.context().effectiveAt()).isEqualTo(EFFECTIVE);
    }

    @Test
    void freezesDirectoryRevisionHashAndDeterministicCandidates() {
        Harness harness = harness(() -> { }, validDirectory());
        var prepared = harness.bridge().prepare(100L, 101L);

        assertThat(prepared.candidates().revision()).isEqualTo(7L);
        assertThat(prepared.candidates().directoryHash())
                .isEqualTo(validDirectory().resultHash());
        assertThat(prepared.candidates().candidateUsers())
                .extracting(item -> item.userId()).containsExactly("701", "702");
        assertThat(prepared.evidence().candidateCount()).isEqualTo(2);
        assertThat(prepared.evidence().canonical()).contains(
                "roleCode=13:ROLE_APPROVER",
                "organizationId=1:8",
                "directoryRevision=1:7");
    }

    @Test
    void fixedOrganizationIsTheOnlyDirectoryQueryScope() {
        Harness harness = harness(() -> { }, validDirectory());
        harness.bridge().prepare(100L, 101L);
        verify(harness.directory()).resolve(any());
    }

    @Test
    void technicalGateIsFailClosedBeforeDirectoryAccess() {
        Harness harness = harness(RoleDirectoryRuntimeTechnicalGate.failClosed(), validDirectory());
        assertThatThrownBy(() -> harness.bridge().prepare(100L, 101L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TECHNICAL_GATE_BLOCKED");
        verify(harness.directory(), never()).resolve(any());
    }

    @Test
    void resolverContractDriftFailsClosed() {
        Harness harness = harness(() -> { }, validDirectory(),
                resolver(ResolverContractHash.of("f".repeat(64))),
                nodeBinding(ResolverContractHash.of("f".repeat(64))));
        assertThatThrownBy(() -> harness.bridge().prepare(100L, 101L))
                .isInstanceOf(RoleDirectoryException.class)
                .hasMessageContaining("frozen contract");
    }

    @Test
    void invalidDirectoryHashFailsClosed() {
        RoleDirectoryResult valid = validDirectory();
        RoleDirectoryResult invalid = new RoleDirectoryResult(valid.roleCode(),
                valid.organizationId(), valid.effectiveAt(), valid.revision(), true,
                valid.members(), "f".repeat(64), valid.contractHash(), valid.source());
        Harness harness = harness(() -> { }, invalid);
        assertThatThrownBy(() -> harness.bridge().prepare(100L, 101L))
                .isInstanceOf(RoleDirectoryException.class)
                .hasMessageContaining("hash mismatch");
    }

    @Test
    void zeroCandidateFailsWithoutCreatingAnEmptyDraft() {
        RoleDirectoryResult empty = RoleDirectoryResult.complete("ROLE_APPROVER", "8",
                EFFECTIVE, 7, List.of(), RoleDirectoryResolver.CONTRACT_HASH.value(),
                "TEST_DIRECTORY");
        Harness harness = harness(() -> { }, empty);
        assertThatThrownBy(() -> harness.bridge().prepare(100L, 101L))
                .isInstanceOf(RoleDirectoryException.class)
                .hasMessageContaining("no effective approval-role member");
    }

    @Test
    void duplicateAssignmentEvidenceFailsClosed() {
        RoleDirectoryMember first = member("701", "A-1");
        RoleDirectoryMember duplicate = member("702", "A-1");
        RoleDirectoryResult result = RoleDirectoryResult.complete("ROLE_APPROVER", "8",
                EFFECTIVE, 7, List.of(first, duplicate),
                RoleDirectoryResolver.CONTRACT_HASH.value(), "TEST_DIRECTORY");
        Harness harness = harness(() -> { }, result);
        assertThatThrownBy(() -> harness.bridge().prepare(100L, 101L))
                .isInstanceOf(RoleCandidateException.class)
                .hasMessageContaining("duplicate assignment");
    }

    @Test
    void directoryFailurePropagatesWithoutFallback() {
        RoleDirectoryPort directory = mock(RoleDirectoryPort.class);
        when(directory.resolve(any())).thenThrow(new IllegalStateException("directory down"));
        Harness harness = harness(() -> { }, directory, resolver(), nodeBinding());
        assertThatThrownBy(() -> harness.bridge().prepare(100L, 101L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("directory down");
    }

    @Test
    void multipleRoleBindingsFailClosedUntilRuntimeSemanticsAreFrozen() {
        Harness harness = harness(() -> { }, validDirectory());
        when(harness.nodeBindings().findByInstanceId(100L))
                .thenReturn(List.of(nodeBinding(), nodeBinding(903L, 2)));
        assertThatThrownBy(() -> harness.bridge().prepare(100L, 101L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MULTI_ROLE_BINDING_RUNTIME_NOT_SUPPORTED");
        verify(harness.directory(), never()).resolve(any());
    }

    @Test
    void legacyUserNeverInvokesRoleDirectory() {
        Harness harness = harness(() -> { }, validDirectory());
        NodeResolverBinding user = new NodeResolverBinding(902L, 900L, 901L,
                100L, 20L, 30L, "NODE_30", AssignmentStrategy.Type.USER,
                ResolverMode.DIRECT, AssignmentStrategy.Type.USER,
                "{\"userId\":701}", "RULE_V1", "{}", "d".repeat(64),
                "e".repeat(64), WorkflowResolverBindingSet.Status.FROZEN,
                ACTIVATED.minusMinutes(1), "USER", 0);
        when(harness.nodeBindings().findByInstanceId(100L)).thenReturn(List.of(user));
        assertThatThrownBy(() -> harness.bridge().prepare(100L, 101L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a frozen ROLE");
        verify(harness.directory(), never()).resolve(any());
    }

    @Test
    void preparedDraftDoesNotRefreshAfterDirectoryChanges() {
        Harness harness = harness(() -> { }, validDirectory());
        var prepared = harness.bridge().prepare(100L, 101L);
        when(harness.directory().resolve(any())).thenReturn(RoleDirectoryResult.complete(
                "ROLE_APPROVER", "8", EFFECTIVE, 8,
                List.of(member("702", "A-2"), member("703", "A-3")),
                RoleDirectoryResolver.CONTRACT_HASH.value(), "TEST_DIRECTORY"));

        assertThat(prepared.evidence().directoryRevision()).isEqualTo(7L);
        assertThat(prepared.candidates().candidateUsers())
                .extracting(item -> item.userId()).containsExactly("701", "702");
        verify(harness.directory()).resolve(any());
    }

    private Harness harness(
            RoleDirectoryRuntimeTechnicalGate gate, RoleDirectoryResult result) {
        RoleDirectoryPort port = mock(RoleDirectoryPort.class);
        when(port.resolve(any())).thenReturn(result);
        return harness(gate, port, resolver(), nodeBinding());
    }

    private Harness harness(
            RoleDirectoryRuntimeTechnicalGate gate, RoleDirectoryPort port,
            WorkflowResolverBinding resolver, NodeResolverBinding nodeBinding) {
        WorkflowInstanceRepository instances = mock(WorkflowInstanceRepository.class);
        WorkflowNodeExecutionRepository executions = mock(WorkflowNodeExecutionRepository.class);
        NodeResolverBindingRepository nodes = mock(NodeResolverBindingRepository.class);
        ResolverBindingRepository resolvers = mock(ResolverBindingRepository.class);
        when(instances.findById(100L)).thenReturn(Optional.of(instance()));
        when(executions.findById(101L)).thenReturn(Optional.of(execution()));
        when(nodes.findByInstanceId(100L)).thenReturn(List.of(nodeBinding));
        when(resolvers.findById(901L)).thenReturn(Optional.of(resolver));
        RoleDirectoryRuntimeBridge bridge = new RoleDirectoryRuntimeBridge(instances,
                executions, nodes, resolvers, gate, new RoleDirectoryResolver(port),
                new RoleCandidateAdapter(10));
        return new Harness(bridge, port, nodes);
    }

    private Harness harness(
            RoleDirectoryRuntimeTechnicalGate gate, RoleDirectoryResult result,
            WorkflowResolverBinding resolver, NodeResolverBinding nodeBinding) {
        RoleDirectoryPort port = mock(RoleDirectoryPort.class);
        when(port.resolve(any())).thenReturn(result);
        return harness(gate, port, resolver, nodeBinding);
    }

    private WorkflowInstance instance() {
        return new WorkflowInstance(100L, "WFI-100", 1L, 20L, "RC2", 2,
                "a".repeat(64), null, "TEST", "B-1", "TEST:B-1", 10L,
                null, null, 1, 700L, 8L, 30L, 101L,
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256,
                WorkflowInstance.Status.RUNNING, null, "{}", "idem",
                "b".repeat(64), 0, "TRACE-1", ACTIVATED.minusMinutes(5),
                null, null, 1);
    }

    private WorkflowNodeExecution execution() {
        return new WorkflowNodeExecution(101L, "WFNE-101", 100L, 20L, 30L,
                "NODE_30", "Node 30", 1, null, null,
                WorkflowNodeExecution.Status.ACTIVE, null,
                ACTIVATED.minusSeconds(1), ACTIVATED, null, null, null,
                "TRACE-1", 1);
    }

    private WorkflowResolverBinding resolver() {
        return resolver(RoleDirectoryResolver.CONTRACT_HASH);
    }

    private WorkflowResolverBinding resolver(ResolverContractHash hash) {
        return new WorkflowResolverBinding(901L, 900L, 100L, 20L,
                ResolverCode.of("ROLE_DIRECTORY"), ResolverVersion.of("ROLE_DIRECTORY_V1"),
                AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL, hash,
                "c".repeat(64), WorkflowResolverBindingSet.Status.FROZEN,
                ACTIVATED.minusMinutes(5), "S6", 0);
    }

    private NodeResolverBinding nodeBinding() {
        return nodeBinding(RoleDirectoryResolver.CONTRACT_HASH);
    }

    private NodeResolverBinding nodeBinding(ResolverContractHash hash) {
        return new NodeResolverBinding(902L, 900L, 901L, 100L, 20L, 30L,
                "NODE_30", AssignmentStrategy.Type.ROLE,
                ResolverMode.CANDIDATE_POOL, AssignmentStrategy.Type.ROLE,
                "{\"roleCode\":\"ROLE_APPROVER\"}", "RULE_V1", "{}",
                "d".repeat(64), "e".repeat(64), 800L, 1, "f".repeat(64),
                hash, "ROLE_APPROVER", OrganizationScopeType.FIXED_ORG, 8L,
                EffectiveTimePolicy.NODE_ACTIVATED_AT,
                "VERSION_NODE_RESOLVER_BINDING_V1",
                WorkflowResolverBindingSet.Status.FROZEN,
                ACTIVATED.minusMinutes(5), "S6", 0);
    }

    private NodeResolverBinding nodeBinding(long id, int order) {
        NodeResolverBinding source = nodeBinding();
        return new NodeResolverBinding(id, source.bindingSetId(),
                source.resolverBindingId(), source.instanceId(),
                source.definitionVersionId(), source.nodeId(),
                source.nodeCodeSnapshot(), source.strategyType(), source.resolverMode(),
                source.targetType(), source.targetValueSnapshot(), source.ruleVersion(),
                source.ruleSnapshot(), source.ruleSnapshotHash(), source.nodeBindingHash(),
                800L + order, order, Character.toString((char) ('a' + order)).repeat(64),
                source.resolverContractHashSnapshot(), source.roleCode(),
                source.organizationScopeType(), source.resolvedOrganizationId(),
                source.effectiveTimePolicy(), source.bindingSchemaVersion(), source.status(),
                source.frozenTime(), source.auditInfo(), source.version());
    }

    private RoleDirectoryResult validDirectory() {
        return RoleDirectoryResult.complete("ROLE_APPROVER", "8", EFFECTIVE, 7,
                List.of(member("702", "A-2"), member("701", "A-1")),
                RoleDirectoryResolver.CONTRACT_HASH.value(), "TEST_DIRECTORY");
    }

    private RoleDirectoryMember member(String userId, String assignmentId) {
        return new RoleDirectoryMember(userId, assignmentId, "ROLE_APPROVER", "8",
                EFFECTIVE.minusSeconds(60), EFFECTIVE.plusSeconds(3600),
                RoleDirectorySourceType.MANUAL_GOVERNANCE,
                "SOURCE-" + assignmentId, 7);
    }

    private record Harness(RoleDirectoryRuntimeBridge bridge,
            RoleDirectoryPort directory, NodeResolverBindingRepository nodeBindings) { }
}
