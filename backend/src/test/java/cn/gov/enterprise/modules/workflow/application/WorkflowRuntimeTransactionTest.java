package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.application.command.StartWorkflowCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowRuntimeApplicationService;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class WorkflowRuntimeTransactionTest {
    @Test
    void startMustRollbackWhenInitialTaskCannotBeSaved() {
        var definitions = org.mockito.Mockito.mock(WorkflowDefinitionRepository.class);
        var versions = org.mockito.Mockito.mock(WorkflowVersionRepository.class);
        var nodes = org.mockito.Mockito.mock(WorkflowNodeRepository.class);
        var instances = org.mockito.Mockito.mock(WorkflowInstanceRepository.class);
        var tasks = org.mockito.Mockito.mock(WorkflowTaskRepository.class);
        var ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
        var security = org.mockito.Mockito.mock(CurrentSecurityContext.class);
        LocalDateTime now = LocalDateTime.now();
        WorkflowDefinition definition = new WorkflowDefinition(1L, "CONTRACT_APPROVAL", "合同审批",
                "CONTRACT", 100L, 30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 1);
        WorkflowVersion workflowVersion = new WorkflowVersion(2L, 1L, 1,
                WorkflowVersion.Status.PUBLISHED, "1.0", "hash", null, now, null, 20L, now, null, 1);
        WorkflowNode node = new WorkflowNode(3L, 2L, "FIRST", "首节点",
                WorkflowNode.NodeType.APPROVAL, 1, WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                WorkflowNode.ApprovalMode.SINGLE, null, WorkflowNode.AssignmentRuleType.USER,
                "{}", null, null, null, false, true, 0);
        when(security.principal()).thenReturn(new SecurityPrincipal(
                20L, "starter", 30L, Set.of(30L), false, false, 0));
        when(definitions.findById(1L)).thenReturn(Optional.of(definition));
        when(versions.findById(2L)).thenReturn(Optional.of(workflowVersion));
        when(nodes.findByVersionId(2L)).thenReturn(java.util.List.of(node));
        when(ids.nextId()).thenReturn(10L, 11L);
        org.mockito.Mockito.doThrow(new IllegalStateException("task persistence failure"))
                .when(tasks).save(any());
        WorkflowRuntimeApplicationService target = new WorkflowRuntimeApplicationService(
                definitions, versions, nodes, instances, tasks, ids, security);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        WorkflowRuntimeApplicationService proxy = (WorkflowRuntimeApplicationService) factory.getProxy();

        assertThatThrownBy(() -> proxy.startWorkflow(command()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("task persistence failure");
        assertThat(transactionManager.begun).isTrue();
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    @Test
    void queryMethodsMustUseReadOnlyTransactions() throws Exception {
        assertThat(WorkflowRuntimeApplicationService.class.getMethod("queryInstance", Long.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(WorkflowRuntimeApplicationService.class.getMethod("queryTasks", Long.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    private StartWorkflowCommand command() {
        return new StartWorkflowCommand(1L, "CONTRACT", "B-1", "CONTRACT:B-1", 100L,
                null, null, 1, "{}", "idem-1");
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
