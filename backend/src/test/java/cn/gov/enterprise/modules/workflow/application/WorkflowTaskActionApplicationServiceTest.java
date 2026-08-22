package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.ProcessWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowTaskActionApplicationService;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTaskAction;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskActionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowTaskActionApplicationServiceTest {
    private final WorkflowTaskRepository tasks = org.mockito.Mockito.mock(WorkflowTaskRepository.class);
    private final WorkflowInstanceRepository instances = org.mockito.Mockito.mock(WorkflowInstanceRepository.class);
    private final WorkflowNodeRepository nodes = org.mockito.Mockito.mock(WorkflowNodeRepository.class);
    private final WorkflowTaskActionRepository actions = org.mockito.Mockito.mock(WorkflowTaskActionRepository.class);
    private final WorkflowIdentityGenerator ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
    private final CurrentSecurityContext security = org.mockito.Mockito.mock(CurrentSecurityContext.class);
    private WorkflowTaskActionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowTaskActionApplicationService(tasks, instances, nodes, actions, ids, security);
        when(security.principal()).thenReturn(principal(20L));
        when(tasks.findById(11L)).thenReturn(Optional.of(task()));
        when(instances.findById(10L)).thenReturn(Optional.of(instance()));
        when(actions.findByTaskIdAndIdempotencyKey(11L, "action-1")).thenReturn(Optional.empty());
        when(ids.nextId()).thenReturn(12L);
    }

    @Test
    void approveShouldUpdateTaskAndInstanceAndPersistAction() {
        WorkflowTaskAction result = service.approve(11L, command());

        assertThat(result.actionType()).isEqualTo(WorkflowTaskAction.ActionType.APPROVE);
        verify(tasks).update(org.mockito.ArgumentMatchers.argThat(
                task -> task.status() == WorkflowTask.Status.APPROVED));
        verify(instances).update(org.mockito.ArgumentMatchers.argThat(
                instance -> instance.status() == WorkflowInstance.Status.APPROVED));
        verify(actions).save(result);
    }

    @Test
    void sameIdempotencyRequestShouldReturnOriginalActionWithoutSecondMutation() {
        service.approve(11L, command());
        ArgumentCaptor<WorkflowTaskAction> captor = ArgumentCaptor.forClass(WorkflowTaskAction.class);
        verify(actions).save(captor.capture());
        when(actions.findByTaskIdAndIdempotencyKey(11L, "action-1"))
                .thenReturn(Optional.of(captor.getValue()));

        WorkflowTaskAction replay = service.approve(11L, command());

        assertThat(replay).isEqualTo(captor.getValue());
        verify(tasks, times(1)).update(any());
        verify(instances, times(1)).update(any());
    }

    @Test
    void assigneeMismatchMustBeDeniedBeforePersistence() {
        WorkflowTask assignedElsewhere = new WorkflowTask(11L, "WFT-11", 10L, 2L, 3L,
                "FIRST", "首节点", 1, "FIRST:1", 99L, "{}",
                WorkflowTask.Status.PENDING, "APPROVE,REJECT", null, null,
                null, null, null, 0);
        when(tasks.findById(11L)).thenReturn(Optional.of(assignedElsewhere));

        assertThatThrownBy(() -> service.reject(11L, command()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("another user");
        verify(tasks, never()).update(any());
        verify(actions, never()).save(any());
    }

    @Test
    void withdrawMustRequireInitiatorAndWithdrawableNode() {
        when(nodes.findById(3L)).thenReturn(Optional.of(node(true)));

        WorkflowTaskAction result = service.withdraw(11L, command());

        assertThat(result.actionType()).isEqualTo(WorkflowTaskAction.ActionType.WITHDRAW);
        verify(tasks).update(org.mockito.ArgumentMatchers.argThat(
                task -> task.status() == WorkflowTask.Status.CANCELLED));
        verify(instances).update(org.mockito.ArgumentMatchers.argThat(
                instance -> instance.status() == WorkflowInstance.Status.WITHDRAWN));
    }

    private ProcessWorkflowTaskCommand command() {
        return new ProcessWorkflowTaskCommand("同意", "action-1");
    }

    private SecurityPrincipal principal(Long userId) {
        return new SecurityPrincipal(userId, "operator", 30L, Set.of(30L), false, false, 0);
    }

    private WorkflowTask task() {
        return WorkflowTask.pending(11L, "WFT-11", instance(), node(true), "FIRST:1", "{}", null);
    }

    private WorkflowInstance instance() {
        LocalDateTime now = LocalDateTime.now();
        WorkflowDefinition definition = new WorkflowDefinition(1L, "CONTRACT_APPROVAL", "合同审批",
                "CONTRACT", 100L, 30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 1);
        WorkflowVersion version = new WorkflowVersion(2L, 1L, 1, WorkflowVersion.Status.PUBLISHED,
                "1.0", "hash", null, now, null, 20L, now, null, 1);
        return WorkflowInstance.running(10L, "WFI-10", definition, version, "CONTRACT", "B-1",
                "CONTRACT:B-1", 100L, null, null, 1, 20L, 30L, 3L,
                null, "start-1", "request-hash", null, now);
    }

    private WorkflowNode node(boolean withdrawAllowed) {
        return new WorkflowNode(3L, 2L, "FIRST", "首节点", WorkflowNode.NodeType.APPROVAL, 1,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL, WorkflowNode.ApprovalMode.SINGLE,
                null, WorkflowNode.AssignmentRuleType.USER, "{}", null, null,
                null, withdrawAllowed, true, 0);
    }
}
