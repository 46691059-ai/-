package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowDefinitionCommand;
import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.command.ReplaceWorkflowNodesCommand;
import cn.gov.enterprise.modules.workflow.application.command.PublishWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowDefinitionApplicationService;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionReleaseRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.util.List;
import java.lang.reflect.Method;
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

class WorkflowTransactionContractTest {
    @Test
    void commandUseCasesAreTransactionalAndQueryIsReadOnly() throws Exception {
        assertReadOnly("createDefinition", false, CreateWorkflowDefinitionCommand.class);
        assertReadOnly("queryDefinition", true, Long.class);
        assertReadOnly("queryVersion", true, Long.class, Long.class);
        assertReadOnly("createVersion", false, Long.class, CreateWorkflowVersionCommand.class);
        assertReadOnly("replaceNodes", false, Long.class, Long.class, ReplaceWorkflowNodesCommand.class);
        assertReadOnly("publishVersion", false, Long.class, Long.class, PublishWorkflowVersionCommand.class);
    }

    @Test
    void versionPersistenceFailureShouldRollback() {
        WorkflowDefinitionRepository definitions = org.mockito.Mockito.mock(WorkflowDefinitionRepository.class);
        WorkflowVersionRepository versions = org.mockito.Mockito.mock(WorkflowVersionRepository.class);
        WorkflowNodeRepository nodes = org.mockito.Mockito.mock(WorkflowNodeRepository.class);
        WorkflowIdentityGenerator ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
        WorkflowVersionReleaseRepository releases = org.mockito.Mockito.mock(WorkflowVersionReleaseRepository.class);
        CurrentSecurityContext security = org.mockito.Mockito.mock(CurrentSecurityContext.class);
        when(definitions.findByIdForUpdate(1L)).thenReturn(Optional.of(
                WorkflowDefinition.draft(1L, "TEST", "测试", "TEST", 10L, null, null)));
        when(versions.nextVersionNo(1L)).thenReturn(1);
        when(ids.nextId()).thenReturn(2L);
        org.mockito.Mockito.doThrow(new IllegalStateException("persistence failure"))
                .when(versions).save(any());
        WorkflowDefinitionApplicationService target = new WorkflowDefinitionApplicationService(
                definitions, versions, nodes, ids, releases, security);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        WorkflowDefinitionApplicationService proxy = (WorkflowDefinitionApplicationService) factory.getProxy();

        assertThatThrownBy(() -> proxy.createVersion(1L, new CreateWorkflowVersionCommand("1.0", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("persistence failure");
        assertThat(transactionManager.begun).isTrue();
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    @Test
    void releaseAuditFailureShouldRollbackAtomicPublication() {
        WorkflowDefinitionRepository definitions = org.mockito.Mockito.mock(WorkflowDefinitionRepository.class);
        WorkflowVersionRepository versions = org.mockito.Mockito.mock(WorkflowVersionRepository.class);
        WorkflowNodeRepository nodes = org.mockito.Mockito.mock(WorkflowNodeRepository.class);
        WorkflowIdentityGenerator ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
        WorkflowVersionReleaseRepository releases = org.mockito.Mockito.mock(WorkflowVersionReleaseRepository.class);
        CurrentSecurityContext security = org.mockito.Mockito.mock(CurrentSecurityContext.class);
        WorkflowDefinition definition = WorkflowDefinition.draft(1L, "WF", "Workflow", "TEST", 10L, 20L, null);
        WorkflowVersion draft = WorkflowVersion.draft(2L, 1L, 1, "1.0", null, null);
        WorkflowNode node = new WorkflowNode(3L, 2L, "APPROVE", "Approve", WorkflowNode.NodeType.APPROVAL, 1,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL, WorkflowNode.ApprovalMode.SINGLE, null,
                WorkflowNode.AssignmentRuleType.USER, "{}", null, null, null, true, true, 0);
        when(definitions.findByIdForUpdate(1L)).thenReturn(Optional.of(definition));
        when(versions.findByIdForUpdate(2L)).thenReturn(Optional.of(draft));
        when(nodes.findByVersionId(2L)).thenReturn(List.of(node));
        when(versions.updateState(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(definitions.updateCurrentVersion(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(security.principal()).thenReturn(new SecurityPrincipal(7L, "publisher", 20L, Set.of(20L), false, false, 0));
        when(ids.nextId()).thenReturn(9L);
        org.mockito.Mockito.doThrow(new IllegalStateException("audit persistence failure"))
                .when(releases).save(any());
        WorkflowDefinitionApplicationService target = new WorkflowDefinitionApplicationService(
                definitions, versions, nodes, ids, releases, security);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        WorkflowDefinitionApplicationService proxy = (WorkflowDefinitionApplicationService) factory.getProxy();

        assertThatThrownBy(() -> proxy.publishVersion(
                1L, 2L, new PublishWorkflowVersionCommand(0, 0, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("audit persistence failure");
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    private void assertReadOnly(String name, boolean expected, Class<?>... parameters) throws Exception {
        Method method = WorkflowDefinitionApplicationService.class.getMethod(name, parameters);
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isEqualTo(expected);
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
