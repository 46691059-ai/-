package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.ProcessWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowLinearExecutionApplicationService;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTaskAction;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskActionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.domain.service.NodeExecutionService;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class WorkflowLinearExecutionApplicationServiceTest {
    private final WorkflowTaskRepository tasks = org.mockito.Mockito.mock(WorkflowTaskRepository.class);
    private final WorkflowTaskActionRepository actions = org.mockito.Mockito.mock(WorkflowTaskActionRepository.class);
    private final WorkflowInstanceRepository instances = org.mockito.Mockito.mock(WorkflowInstanceRepository.class);
    private final WorkflowNodeExecutionRepository executions = org.mockito.Mockito.mock(WorkflowNodeExecutionRepository.class);
    private final WorkflowTransitionRepository transitions = org.mockito.Mockito.mock(WorkflowTransitionRepository.class);
    private final WorkflowNodeRepository nodes = org.mockito.Mockito.mock(WorkflowNodeRepository.class);
    private final WorkflowIdentityGenerator ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
    private final CurrentSecurityContext security = org.mockito.Mockito.mock(CurrentSecurityContext.class);
    private final WorkflowTaskAssignmentSnapshotRepository assignmentSnapshots =
            org.mockito.Mockito.mock(WorkflowTaskAssignmentSnapshotRepository.class);
    private final Map<Long, WorkflowTask> taskStore = new HashMap<>();
    private final Map<Long, WorkflowTaskAction> actionStore = new HashMap<>();
    private final Map<Long, WorkflowNodeExecution> executionStore = new HashMap<>();
    private WorkflowInstance currentInstance;
    private WorkflowLinearExecutionApplicationService service;

    @BeforeEach
    void setUp() {
        WorkflowNode a = node(3L, "A", 1);
        WorkflowNodeExecution first = new NodeExecutionService().enter(7L, "WFNE-7",
                baseInstance(a.id()), a, 1, null, null, null, LocalDateTime.now());
        currentInstance = baseInstance(a.id()).pointTo(a.id(), first.id());
        WorkflowTask firstTask = WorkflowTask.pendingLinear(8L, "WFT-8", currentInstance,
                a, first, 20L, a.assignmentRuleConfig(), null);
        executionStore.put(first.id(), first);
        taskStore.put(firstTask.id(), firstTask);

        when(security.principal()).thenReturn(new SecurityPrincipal(
                20L, "approver", 30L, Set.of(30L), false, false, 0));
        when(ids.nextId()).thenAnswer(invocation -> sequence.incrementAndGet());
        when(tasks.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(taskStore.get(invocation.getArgument(0))));
        when(tasks.findByIdForUpdate(anyLong())).thenAnswer(invocation -> Optional.ofNullable(taskStore.get(invocation.getArgument(0))));
        when(tasks.findByNodeExecutionId(anyLong())).thenAnswer(invocation -> taskStore.values().stream()
                .filter(task -> invocation.getArgument(0).equals(task.nodeExecutionId())).findFirst());
        doAnswer(invocation -> { WorkflowTask task = invocation.getArgument(0); taskStore.put(task.id(), task); return null; })
                .when(tasks).save(any());
        doAnswer(invocation -> { WorkflowTask task = invocation.getArgument(0); taskStore.put(task.id(), task); return null; })
                .when(tasks).update(any());
        when(instances.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(currentInstance));
        when(instances.findByIdForUpdate(anyLong())).thenAnswer(invocation -> Optional.ofNullable(currentInstance));
        doAnswer(invocation -> { currentInstance = invocation.getArgument(0); return null; }).when(instances).update(any());
        when(executions.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(executionStore.get(invocation.getArgument(0))));
        when(executions.findByInstanceId(anyLong())).thenAnswer(invocation -> new ArrayList<>(executionStore.values()));
        doAnswer(invocation -> { WorkflowNodeExecution item = invocation.getArgument(0); executionStore.put(item.id(), item); return null; })
                .when(executions).save(any());
        when(executions.updateState(any(), anyInt())).thenAnswer(invocation -> {
            WorkflowNodeExecution item = invocation.getArgument(0);
            executionStore.put(item.id(), item);
            return true;
        });
        when(actions.findByTaskIdAndIdempotencyKey(anyLong(), any())).thenAnswer(invocation -> actionStore.values().stream()
                .filter(action -> action.taskId().equals(invocation.getArgument(0))
                        && action.idempotencyKey().equals(invocation.getArgument(1))).findFirst());
        doAnswer(invocation -> { WorkflowTaskAction action = invocation.getArgument(0); actionStore.put(action.id(), action); return null; })
                .when(actions).save(any());
        when(nodes.findById(4L)).thenReturn(Optional.of(node(4L, "B", 2)));
        when(nodes.findById(5L)).thenReturn(Optional.of(node(5L, "C", 3)));
        when(transitions.findOutgoing(2L, 3L, WorkflowTransition.TriggerType.APPROVE))
                .thenReturn(List.of(transition(10L, 3L, 4L, "A_B")));
        when(transitions.findOutgoing(2L, 4L, WorkflowTransition.TriggerType.APPROVE))
                .thenReturn(List.of(transition(11L, 4L, 5L, "B_C")));
        when(transitions.findOutgoing(2L, 5L, WorkflowTransition.TriggerType.APPROVE))
                .thenReturn(List.of());
        service = new WorkflowLinearExecutionApplicationService(tasks, actions, instances,
                executions, transitions, nodes, ids, security, assignmentSnapshots);
    }

    private final AtomicLong sequence = new AtomicLong(100L);

    @Test
    void aToBToCShouldAdvanceAndEndOnlyAfterC() {
        var first = service.complete(8L, command("a"));
        assertThat(first.instanceStatus()).isEqualTo(WorkflowInstance.Status.RUNNING);
        assertThat(first.nextNodeStatus()).isEqualTo(WorkflowNodeExecution.Status.ACTIVE);
        var second = service.complete(first.nextTaskId(), command("b"));
        assertThat(second.instanceStatus()).isEqualTo(WorkflowInstance.Status.RUNNING);
        var third = service.complete(second.nextTaskId(), command("c"));
        assertThat(third.instanceStatus()).isEqualTo(WorkflowInstance.Status.APPROVED);
        assertThat(third.nextTaskId()).isNull();
        assertThat(executionStore.values()).hasSize(3);
    }

    @Test
    void repeatedTaskActionMustReturnOriginalResultWithoutNewMutation() {
        var first = service.complete(8L, command("same"));
        int actionsAfterFirst = actionStore.size();
        int executionsAfterFirst = executionStore.size();
        var replay = service.complete(8L, command("same"));
        assertThat(replay.actionId()).isEqualTo(first.actionId());
        assertThat(actionStore).hasSize(actionsAfterFirst);
        assertThat(executionStore).hasSize(executionsAfterFirst);
        verify(assignmentSnapshots, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void createdTaskAndSnapshotMustContainTheSameResolvedUser() {
        var result = service.complete(8L, command("assignment-consistency"));
        ArgumentCaptor<AssignmentSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(AssignmentSnapshot.class);

        verify(assignmentSnapshots).save(snapshotCaptor.capture());
        AssignmentSnapshot snapshot = snapshotCaptor.getValue();
        WorkflowTask createdTask = taskStore.get(result.nextTaskId());

        assertThat(snapshot.taskId()).isEqualTo(createdTask.id());
        assertThat(snapshot.nodeExecutionId()).isEqualTo(createdTask.nodeExecutionId());
        assertThat(snapshot.resolvedUserIds()).containsExactly(createdTask.assigneeUserId());
        assertThat(snapshot.targetSnapshot()).isEqualTo(createdTask.candidateSnapshot());
        assertThat(snapshot.auditInfo()).contains(ExplicitUserResolver.VERSION);
        assertThat(currentInstance.requireResolverVersionBinding().resolverVersion())
                .isEqualTo(ExplicitUserResolver.RESOLVER_VERSION);
        assertThat(currentInstance.requireResolverVersionBinding().contractHash())
                .isEqualTo(ExplicitUserResolver.CONTRACT_HASH);
    }

    @Test
    void optimisticConflictMustPreventNextNodeCreation() {
        doReturn(false).when(executions).updateState(any(), anyInt());
        assertThatThrownBy(() -> service.complete(8L, command("race")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("concurrently");
        verify(executions, never()).save(any());
    }

    @Test
    void actionPersistenceFailureMustStopAllFollowingMutations() {
        doThrow(new BusinessException("TEST", "forced rollback")).when(actions).save(any());
        assertThatThrownBy(() -> service.complete(8L, command("rollback")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("forced rollback");
        verify(tasks, never()).update(any());
        verify(executions, never()).updateState(any(), anyInt());
        verify(instances, never()).update(any());
    }

    @Test
    void assignmentSnapshotFailureMustPreventInstanceAdvance() {
        doThrow(new BusinessException("TEST", "snapshot persistence failure"))
                .when(assignmentSnapshots).save(any());
        assertThatThrownBy(() -> service.complete(8L, command("snapshot-failure")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("snapshot persistence failure");
        verify(instances, never()).update(any());
    }

    @Test
    void assignmentSnapshotFailureMustRollbackCompletionTransaction() {
        doThrow(new BusinessException("TEST", "snapshot persistence failure"))
                .when(assignmentSnapshots).save(any());
        RecordingTransactionManager transactions = new RecordingTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(interceptor);
        WorkflowLinearExecutionApplicationService proxy =
                (WorkflowLinearExecutionApplicationService) factory.getProxy();

        assertThatThrownBy(() -> proxy.complete(8L, command("transaction-failure")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("snapshot persistence failure");
        assertThat(transactions.begun).isTrue();
        assertThat(transactions.rolledBack).isTrue();
        assertThat(transactions.committed).isFalse();
    }

    @Test
    void unsupportedOrgAssignmentMustBeRejectedWithoutSnapshot() {
        WorkflowNode orgNode = new WorkflowNode(4L, 2L, "B", "B",
                WorkflowNode.NodeType.APPROVAL, 2,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                WorkflowNode.ApprovalMode.SINGLE, null,
                WorkflowNode.AssignmentRuleType.ORG, "{\"orgCode\":\"ORG-1\"}",
                null, null, null, true, true, 0);
        when(nodes.findById(4L)).thenReturn(Optional.of(orgNode));

        assertThatThrownBy(() -> service.complete(8L, command("org-not-supported")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("explicit USER assignment");
        verify(assignmentSnapshots, never()).save(any());
        verify(instances, never()).update(any());
    }

    private ProcessWorkflowTaskCommand command(String key) {
        return new ProcessWorkflowTaskCommand("同意", key);
    }

    private WorkflowInstance baseInstance(Long nodeId) {
        LocalDateTime now = LocalDateTime.now();
        WorkflowDefinition definition = new WorkflowDefinition(1L, "LINEAR", "线性流程", "TEST",
                100L, 30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 0);
        WorkflowVersion version = new WorkflowVersion(2L, 1L, 1, WorkflowVersion.Status.PUBLISHED,
                "2.0", "a".repeat(64), null, now, null, 20L, now, null,
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256, 0);
        ResolverVersionBinding binding = ResolverVersionBinding.freeze(
                6L, new ExplicitUserResolver().descriptor());
        return WorkflowInstance.runningLinear(6L, "WFI-6", definition, version, binding,
                "TEST", "1",
                "TEST:1", 100L, null, null, 1, 20L, 30L, nodeId, null,
                "start", "b".repeat(64), null, now);
    }

    private WorkflowNode node(Long id, String code, int order) {
        return new WorkflowNode(id, 2L, code, code, WorkflowNode.NodeType.APPROVAL, order,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                WorkflowNode.ApprovalMode.SINGLE, null, WorkflowNode.AssignmentRuleType.USER,
                "{\"userId\":20}", null, null, null, true, true, 0);
    }

    private WorkflowTransition transition(Long id, Long from, Long to, String code) {
        return new WorkflowTransition(id, 2L, code, code, from, to,
                WorkflowTransition.TriggerType.APPROVE, WorkflowTransition.RouteType.DIRECT,
                1, null, true, 0);
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private boolean begun;
        private boolean committed;
        private boolean rolledBack;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { begun = true; }
        @Override protected void doCommit(DefaultTransactionStatus status) { committed = true; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
    }
}
