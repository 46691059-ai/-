package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class WorkflowTaskActionTransactionTest {
    @Test
    void actionPersistenceFailureMustRollbackTaskAndInstanceUpdates() {
        WorkflowTaskRepository tasks = org.mockito.Mockito.mock(WorkflowTaskRepository.class);
        WorkflowInstanceRepository instances = org.mockito.Mockito.mock(WorkflowInstanceRepository.class);
        WorkflowNodeRepository nodes = org.mockito.Mockito.mock(WorkflowNodeRepository.class);
        WorkflowTaskActionRepository actions = org.mockito.Mockito.mock(WorkflowTaskActionRepository.class);
        WorkflowIdentityGenerator ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
        CurrentSecurityContext security = org.mockito.Mockito.mock(CurrentSecurityContext.class);
        WorkflowInstance instance = instance();
        WorkflowTask task = WorkflowTask.pending(11L, "WFT-11", instance, node(), "FIRST:1", "{}", null);
        when(security.principal()).thenReturn(new SecurityPrincipal(
                20L, "operator", 30L, Set.of(30L), false, false, 0));
        when(tasks.findById(11L)).thenReturn(Optional.of(task));
        when(instances.findById(10L)).thenReturn(Optional.of(instance));
        when(actions.findByTaskIdAndIdempotencyKey(11L, "action-1")).thenReturn(Optional.empty());
        when(ids.nextId()).thenReturn(12L);
        org.mockito.Mockito.doThrow(new IllegalStateException("action persistence failure"))
                .when(actions).save(any(WorkflowTaskAction.class));
        WorkflowTaskActionApplicationService target = new WorkflowTaskActionApplicationService(
                tasks, instances, nodes, actions, ids, security);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        WorkflowTaskActionApplicationService proxy =
                (WorkflowTaskActionApplicationService) factory.getProxy();

        assertThatThrownBy(() -> proxy.approve(
                11L, new ProcessWorkflowTaskCommand("同意", "action-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("action persistence failure");
        assertThat(transactionManager.begun).isTrue();
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
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

    private WorkflowNode node() {
        return new WorkflowNode(3L, 2L, "FIRST", "首节点", WorkflowNode.NodeType.APPROVAL, 1,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL, WorkflowNode.ApprovalMode.SINGLE,
                null, WorkflowNode.AssignmentRuleType.USER, "{}", null, null,
                null, true, true, 0);
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
