package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.application.command.StartWorkflowCommand;
import cn.gov.enterprise.modules.workflow.application.service.VersionResolverBindingInstanceFreezer;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowAssignmentResolverApplicationService;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowResolverBindingApplicationService;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowRuntimeApplicationService;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBindingSet;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.NodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingSetRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class WorkflowRoleInstanceFreezeIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.now();

    @Test
    void roleInstanceStartMustFreezeEvidenceAndInitialExecutionWithoutTaskOrCandidateRuntime() {
        Harness harness = harness(null);
        WorkflowInstance result = harness.service().startWorkflow(command());

        assertThat(result.status()).isEqualTo(WorkflowInstance.Status.RUNNING);
        assertThat(result.currentNodeExecutionId()).isEqualTo(101L);
        verify(harness.instances()).save(any());
        verify(harness.sets()).save(any());
        verify(harness.resolvers()).saveAll(any());
        verify(harness.nodeBindings()).saveAll(any());
        verify(harness.executions()).save(any());
        verify(harness.tasks(), never()).save(any());
        verify(harness.assignmentSnapshots(), never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(FailurePoint.class)
    void everyInstanceFreezePersistenceFailureMustRollbackAll(FailurePoint failure) {
        Harness harness = harness(failure);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        WorkflowRuntimeApplicationService proxy = transactionalProxy(harness.service(), transactionManager);

        assertThatThrownBy(() -> proxy.startWorkflow(command()))
                .isInstanceOf(RuntimeException.class);
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    private Harness harness(FailurePoint failure) {
        WorkflowDefinitionRepository definitions = mock(WorkflowDefinitionRepository.class);
        WorkflowVersionRepository versions = mock(WorkflowVersionRepository.class);
        WorkflowNodeRepository nodes = mock(WorkflowNodeRepository.class);
        WorkflowInstanceRepository instances = mock(WorkflowInstanceRepository.class);
        WorkflowTaskRepository tasks = mock(WorkflowTaskRepository.class);
        WorkflowIdentityGenerator ids = mock(WorkflowIdentityGenerator.class);
        CurrentSecurityContext security = mock(CurrentSecurityContext.class);
        WorkflowNodeExecutionRepository executions = mock(WorkflowNodeExecutionRepository.class);
        WorkflowTransitionRepository transitions = mock(WorkflowTransitionRepository.class);
        WorkflowTaskAssignmentSnapshotRepository assignmentSnapshots =
                mock(WorkflowTaskAssignmentSnapshotRepository.class);
        WorkflowAssignmentResolverApplicationService resolverService =
                mock(WorkflowAssignmentResolverApplicationService.class);
        ResolverBindingSetRepository sets = mock(ResolverBindingSetRepository.class);
        ResolverBindingRepository resolvers = mock(ResolverBindingRepository.class);
        NodeResolverBindingRepository nodeBindings = mock(NodeResolverBindingRepository.class);
        WorkflowResolverBindingApplicationService bindingService =
                new WorkflowResolverBindingApplicationService(
                        sets, resolvers, nodeBindings, instances, ids, resolverService, security);
        VersionResolverBindingInstanceFreezer freezer = mock(VersionResolverBindingInstanceFreezer.class);

        WorkflowDefinition definition = new WorkflowDefinition(
                1L, "RC2", "RC2", "TEST", 10L, 8L,
                WorkflowDefinition.Status.ACTIVE, 20L, null, 1);
        WorkflowVersion version = new WorkflowVersion(
                20L, 1L, 2, WorkflowVersion.Status.PUBLISHED, "2.0", "a".repeat(64), null,
                NOW.minusMinutes(1), null, 7L, NOW.minusMinutes(1), null,
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256,
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                "b".repeat(64), 2, "VERSION_RESOLVER_BINDING_MANIFEST_V1", 2);
        WorkflowNode first = node(30L, 1);
        WorkflowNode second = node(40L, 2);
        WorkflowTransition transition = new WorkflowTransition(
                50L, 20L, "A_TO_B", "A to B", 30L, 40L,
                WorkflowTransition.TriggerType.APPROVE, WorkflowTransition.RouteType.DIRECT,
                1, null, true, 0);
        when(definitions.findById(1L)).thenReturn(Optional.of(definition));
        when(versions.findById(20L)).thenReturn(Optional.of(version));
        when(nodes.findByVersionId(20L)).thenReturn(List.of(first, second));
        when(transitions.findByVersionId(20L)).thenReturn(List.of(transition));
        when(instances.findByEnterpriseIdAndIdempotencyKey(10L, "idem"))
                .thenReturn(Optional.empty());
        when(security.principal()).thenReturn(new SecurityPrincipal(
                7L, "starter", 8L, Set.of(8L), false, false, 0));
        when(ids.nextId()).thenReturn(100L, 101L);
        when(freezer.prepare(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(frozen());
        if (failure == FailurePoint.INSTANCE_SAVE) {
            org.mockito.Mockito.doThrow(new IllegalStateException("instance save failure"))
                    .when(instances).save(any());
        } else if (failure == FailurePoint.BINDING_SET_SAVE) {
            org.mockito.Mockito.doThrow(new IllegalStateException("set save failure"))
                    .when(sets).save(any());
        } else if (failure == FailurePoint.RESOLVER_BINDING_SAVE) {
            org.mockito.Mockito.doThrow(new IllegalStateException("binding save failure"))
                    .when(resolvers).saveAll(any());
        } else if (failure == FailurePoint.NODE_SNAPSHOT_SAVE) {
            org.mockito.Mockito.doThrow(new IllegalStateException("snapshot save failure"))
                    .when(nodeBindings).saveAll(any());
        } else if (failure == FailurePoint.INITIAL_EXECUTION_SAVE) {
            org.mockito.Mockito.doThrow(new IllegalStateException("execution save failure"))
                    .when(executions).save(any());
        }
        WorkflowRuntimeApplicationService service = new WorkflowRuntimeApplicationService(
                definitions, versions, nodes, instances, tasks, ids, security,
                executions, transitions, assignmentSnapshots, resolverService,
                bindingService, freezer);
        return new Harness(service, instances, tasks, executions, assignmentSnapshots,
                sets, resolvers, nodeBindings);
    }

    private WorkflowResolverBindingApplicationService.FrozenBindings frozen() {
        WorkflowResolverBindingSet set = new WorkflowResolverBindingSet(
                900L, 100L, 1L, 20L, "VERSION_RESOLVER_BINDING_MANIFEST_V1",
                "b".repeat(64), 2, WorkflowResolverBindingSet.Status.FROZEN,
                NOW, "RC2-S5", 0);
        WorkflowResolverBinding resolver = new WorkflowResolverBinding(
                901L, 900L, 100L, 20L, ResolverCode.of("ROLE_DIRECTORY"),
                ResolverVersion.of("ROLE_DIRECTORY_V1"), AssignmentStrategy.Type.ROLE,
                ResolverMode.CANDIDATE_POOL, ResolverContractHash.of("c".repeat(64)),
                "d".repeat(64), WorkflowResolverBindingSet.Status.FROZEN, NOW, "RC2-S5", 0);
        return new WorkflowResolverBindingApplicationService.FrozenBindings(
                set, List.of(resolver), List.of(roleNode(902L, 30L, 1, "ROLE_APPROVER"),
                roleNode(903L, 40L, 1, "ROLE_REVIEWER")));
    }

    private NodeResolverBinding roleNode(Long id, Long nodeId, int order, String roleCode) {
        return new NodeResolverBinding(id, 900L, 901L, 100L, 20L, nodeId,
                "NODE_" + nodeId, AssignmentStrategy.Type.ROLE,
                ResolverMode.CANDIDATE_POOL, AssignmentStrategy.Type.ROLE,
                "{\"roleCode\":\"" + roleCode + "\",\"organizationId\":8}",
                "VERSION_NODE_RESOLVER_BINDING_V1", "rule", "e".repeat(64),
                "f".repeat(64), nodeId * 10 + order, order, "f".repeat(64),
                ResolverContractHash.of("c".repeat(64)), roleCode,
                OrganizationScopeType.FIXED_ORG, 8L,
                EffectiveTimePolicy.NODE_ACTIVATED_AT,
                "VERSION_NODE_RESOLVER_BINDING_V1",
                WorkflowResolverBindingSet.Status.FROZEN, NOW, "RC2-S5", 0);
    }

    private WorkflowNode node(Long id, int order) {
        return new WorkflowNode(id, 20L, "NODE_" + id, "Node " + id,
                WorkflowNode.NodeType.APPROVAL, order,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                WorkflowNode.ApprovalMode.SINGLE, null,
                WorkflowNode.AssignmentRuleType.RULE, "{}", null, null,
                null, true, true, 0);
    }

    private StartWorkflowCommand command() {
        return new StartWorkflowCommand(
                1L, "TEST", "B-1", "TEST:B-1", 10L,
                null, null, 1, "{}", "idem");
    }

    private WorkflowRuntimeApplicationService transactionalProxy(
            WorkflowRuntimeApplicationService target,
            RecordingTransactionManager transactionManager) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        return (WorkflowRuntimeApplicationService) factory.getProxy();
    }

    private enum FailurePoint {
        INSTANCE_SAVE, BINDING_SET_SAVE, RESOLVER_BINDING_SAVE,
        NODE_SNAPSHOT_SAVE, INITIAL_EXECUTION_SAVE
    }

    private record Harness(
            WorkflowRuntimeApplicationService service,
            WorkflowInstanceRepository instances, WorkflowTaskRepository tasks,
            WorkflowNodeExecutionRepository executions,
            WorkflowTaskAssignmentSnapshotRepository assignmentSnapshots,
            ResolverBindingSetRepository sets, ResolverBindingRepository resolvers,
            NodeResolverBindingRepository nodeBindings) { }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private boolean committed;
        private boolean rolledBack;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { committed = true; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
    }
}
