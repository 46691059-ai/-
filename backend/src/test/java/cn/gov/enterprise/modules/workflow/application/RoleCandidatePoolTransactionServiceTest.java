package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.application.service.RoleCandidatePoolTransactionService;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBindingSet;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolMemberRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateAdapter;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateContext;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectorySourceType;
import cn.gov.enterprise.modules.workflow.domain.role.runtime.PreparedRoleCandidatePool;
import cn.gov.enterprise.modules.workflow.domain.role.runtime.RoleDirectoryCandidateEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.runtime.RoleDirectoryRuntimeContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class RoleCandidatePoolTransactionServiceTest {
    private static final LocalDateTime ACTIVATED =
            LocalDateTime.of(2026, 8, 24, 9, 30);

    @Test
    void freezesTaskAssignmentPoolAndMembersInOneBoundary() {
        Harness harness = harness();
        CandidatePool result = harness.service().freeze(prepared());

        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.effectiveTime()).isEqualTo(ACTIVATED);
        assertThat(result.members()).extracting(item -> item.candidateUserId())
                .containsExactly(701L, 702L);
        assertThat(result.members()).allSatisfy(member -> {
            assertThat(member.sourceRefSnapshot())
                    .isEqualTo("ROLE_DIRECTORY:ROLE_APPROVER:8:7");
            assertThat(member.eligibilitySnapshot())
                    .contains("directoryResultHash", "directoryRevision");
        });
        verify(harness.tasks()).save(any());
        verify(harness.assignments()).save(any());
        verify(harness.pools()).save(any());
        verify(harness.members()).saveAll(any());
    }

    @Test
    void retryReturnsMatchingFrozenPoolWithoutDuplicateWrites() {
        Harness harness = harness();
        PreparedRoleCandidatePool prepared = prepared();
        CandidatePool first = harness.service().freeze(prepared);
        ArgumentCaptor<WorkflowTask> taskCaptor = ArgumentCaptor.forClass(WorkflowTask.class);
        verify(harness.tasks()).save(taskCaptor.capture());
        when(harness.tasks().findByNodeExecutionId(101L))
                .thenReturn(Optional.of(taskCaptor.getValue()));
        when(harness.pools().findByTaskId(taskCaptor.getValue().id()))
                .thenReturn(Optional.of(first));

        CandidatePool retry = harness.service().freeze(prepared);

        assertThat(retry).isEqualTo(first);
        verify(harness.tasks()).save(any());
        verify(harness.pools()).save(any());
        verify(harness.members()).saveAll(any());
    }

    @Test
    void staleNodeActivationFailsBeforeAnyWrite() {
        Harness harness = harness();
        WorkflowNodeExecution stale = new WorkflowNodeExecution(101L, "WFNE-101",
                100L, 20L, 30L, "NODE_30", "Node 30", 1, null, null,
                WorkflowNodeExecution.Status.ACTIVE, null,
                ACTIVATED.minusSeconds(1), ACTIVATED.plusSeconds(1), null, null,
                null, "TRACE-1", 2);
        when(harness.executions().findByIdForUpdate(101L)).thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> harness.service().freeze(prepared()))
                .hasMessageContaining("activation changed");
        verify(harness.tasks(), never()).save(any());
        verify(harness.pools(), never()).save(any());
    }

    @Test
    void poolPersistenceFailureRollsBackTaskAndAssignment() {
        Harness harness = harness();
        doThrow(new IllegalStateException("pool save failure"))
                .when(harness.pools()).save(any());
        RecordingTransactionManager transactions = new RecordingTransactionManager();
        RoleCandidatePoolTransactionService proxy = proxy(harness.service(), transactions);

        assertThatThrownBy(() -> proxy.freeze(prepared()))
                .hasMessageContaining("pool save failure");
        assertThat(transactions.rolledBack).isTrue();
        assertThat(transactions.committed).isFalse();
        verify(harness.members(), never()).saveAll(any());
    }

    @Test
    void memberPersistenceFailureRollsBackEntirePool() {
        Harness harness = harness();
        doThrow(new IllegalStateException("member save failure"))
                .when(harness.members()).saveAll(any());
        RecordingTransactionManager transactions = new RecordingTransactionManager();
        RoleCandidatePoolTransactionService proxy = proxy(harness.service(), transactions);

        assertThatThrownBy(() -> proxy.freeze(prepared()))
                .hasMessageContaining("member save failure");
        assertThat(transactions.rolledBack).isTrue();
        assertThat(transactions.committed).isFalse();
    }

    @Test
    void transactionServiceHasNoClaimAdmissionOrRealtimeEligibilityDependency() {
        assertThat(RoleCandidatePoolTransactionService.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(List.of(constructor.getParameterTypes()))
                        .noneMatch(type -> type.getName().contains("Claim")
                                || type.getName().contains("Admission")
                                || type.getName().contains("Eligibility")));
    }

    private Harness harness() {
        WorkflowTaskRepository tasks = mock(WorkflowTaskRepository.class);
        WorkflowTaskAssignmentSnapshotRepository assignments =
                mock(WorkflowTaskAssignmentSnapshotRepository.class);
        CandidatePoolRepository pools = mock(CandidatePoolRepository.class);
        CandidatePoolMemberRepository members = mock(CandidatePoolMemberRepository.class);
        WorkflowNodeExecutionRepository executions =
                mock(WorkflowNodeExecutionRepository.class);
        WorkflowIdentityGenerator ids = mock(WorkflowIdentityGenerator.class);
        when(executions.findByIdForUpdate(101L)).thenReturn(Optional.of(execution()));
        when(tasks.findByNodeExecutionId(101L)).thenReturn(Optional.empty());
        when(ids.nextId()).thenReturn(1000L, 1001L, 1002L, 1003L, 1004L);
        return new Harness(new RoleCandidatePoolTransactionService(tasks, assignments,
                pools, members, executions, ids, (scope, at) -> true), tasks, assignments, pools,
                members, executions);
    }

    private PreparedRoleCandidatePool prepared() {
        WorkflowInstance instance = instance();
        WorkflowNodeExecution execution = execution();
        WorkflowResolverBinding resolver = resolver();
        NodeResolverBinding node = nodeBinding();
        RoleDirectoryRuntimeContext context = RoleDirectoryRuntimeContext.from(
                instance, execution, node, resolver);
        Instant effective = context.effectiveAt();
        RoleDirectoryResult directory = RoleDirectoryResult.complete("ROLE_APPROVER",
                "8", effective, 7,
                List.of(member("702", "A-2", effective),
                        member("701", "A-1", effective)),
                RoleDirectoryResolver.CONTRACT_HASH.value(), "TEST_DIRECTORY");
        var candidate = new RoleCandidateAdapter(10).adapt(
                new RoleCandidateContext(100L, 101L, "ROLE_APPROVER", "8",
                        effective, resolver.asLegacyBinding()), directory);
        return new PreparedRoleCandidatePool(instance, execution, node, resolver,
                context, candidate, RoleDirectoryCandidateEvidence.freeze(context, candidate));
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
        return new WorkflowResolverBinding(901L, 900L, 100L, 20L,
                ResolverCode.of("ROLE_DIRECTORY"), ResolverVersion.of("ROLE_DIRECTORY_V1"),
                AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL,
                RoleDirectoryResolver.CONTRACT_HASH, "c".repeat(64),
                WorkflowResolverBindingSet.Status.FROZEN,
                ACTIVATED.minusMinutes(5), "S6", 0);
    }

    private NodeResolverBinding nodeBinding() {
        return new NodeResolverBinding(902L, 900L, 901L, 100L, 20L, 30L,
                "NODE_30", AssignmentStrategy.Type.ROLE,
                ResolverMode.CANDIDATE_POOL, AssignmentStrategy.Type.ROLE,
                "{\"roleCode\":\"ROLE_APPROVER\"}", "RULE_V1", "{}",
                "d".repeat(64), "e".repeat(64), 800L, 1, "f".repeat(64),
                RoleDirectoryResolver.CONTRACT_HASH, "ROLE_APPROVER",
                OrganizationScopeType.FIXED_ORG, 8L,
                EffectiveTimePolicy.NODE_ACTIVATED_AT,
                "VERSION_NODE_RESOLVER_BINDING_V1",
                WorkflowResolverBindingSet.Status.FROZEN,
                ACTIVATED.minusMinutes(5), "S6", 0);
    }

    private RoleDirectoryMember member(
            String userId, String assignmentId, Instant effective) {
        return new RoleDirectoryMember(userId, assignmentId, "ROLE_APPROVER", "8",
                effective.minusSeconds(60), effective.plusSeconds(3600),
                RoleDirectorySourceType.MANUAL_GOVERNANCE,
                "SOURCE-" + assignmentId, 7);
    }

    private RoleCandidatePoolTransactionService proxy(
            RoleCandidatePoolTransactionService target,
            RecordingTransactionManager transactionManager) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        return (RoleCandidatePoolTransactionService) factory.getProxy();
    }

    private record Harness(RoleCandidatePoolTransactionService service,
            WorkflowTaskRepository tasks,
            WorkflowTaskAssignmentSnapshotRepository assignments,
            CandidatePoolRepository pools, CandidatePoolMemberRepository members,
            WorkflowNodeExecutionRepository executions) { }

    private static final class RecordingTransactionManager
            extends AbstractPlatformTransactionManager {
        private boolean committed;
        private boolean rolledBack;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(
                Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }
}
