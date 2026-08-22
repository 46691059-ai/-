package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowNodeExecutionCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowMultiNodeRuntimeApplicationService;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.LocalDateTime;
import java.util.List;
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

class WorkflowMultiNodeApplicationServiceTest {
    @Test
    void transitionAndTrajectoryQueriesMustBeReadOnly() throws Exception {
        assertThat(WorkflowMultiNodeRuntimeApplicationService.class
                .getMethod("queryTransitions", Long.class, Long.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(WorkflowMultiNodeRuntimeApplicationService.class
                .getMethod("queryNodeExecutions", Long.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void queryTransitionsMustReturnOnlyTheOwnedVersionGraph() {
        Fixture fixture = fixture();
        WorkflowTransition edge = new WorkflowTransition(50L, 2L, "T-1", "Next", 3L, 4L,
                WorkflowTransition.TriggerType.APPROVE, WorkflowTransition.RouteType.DIRECT,
                1, null, true, 0);
        when(fixture.definitions.findById(1L)).thenReturn(Optional.of(fixture.definition));
        when(fixture.versions.findById(2L)).thenReturn(Optional.of(fixture.workflowVersion));
        when(fixture.transitions.findByVersionId(2L)).thenReturn(List.of(edge));

        assertThat(fixture.service.queryTransitions(1L, 2L)).containsExactly(edge);
    }

    @Test
    void createNodeExecutionMustRollbackWhenPersistenceFails() {
        Fixture fixture = fixture();
        when(fixture.instances.findById(10L)).thenReturn(Optional.of(fixture.instance));
        when(fixture.nodes.findById(3L)).thenReturn(Optional.of(fixture.node));
        when(fixture.executions.findByInstanceIdAndNodeIdAndVisitNo(10L, 3L, 1))
                .thenReturn(Optional.empty());
        when(fixture.ids.nextId()).thenReturn(20L);
        org.mockito.Mockito.doThrow(new IllegalStateException("execution persistence failure"))
                .when(fixture.executions).save(any());
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(fixture.service);
        factory.addAdvice(interceptor);
        WorkflowMultiNodeRuntimeApplicationService proxy =
                (WorkflowMultiNodeRuntimeApplicationService) factory.getProxy();

        assertThatThrownBy(() -> proxy.createNodeExecution(
                new CreateWorkflowNodeExecutionCommand(10L, 3L, 1, null, null, "trace")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("execution persistence failure");
        assertThat(transactionManager.begun).isTrue();
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    private Fixture fixture() {
        var definitions = org.mockito.Mockito.mock(WorkflowDefinitionRepository.class);
        var versions = org.mockito.Mockito.mock(WorkflowVersionRepository.class);
        var instances = org.mockito.Mockito.mock(WorkflowInstanceRepository.class);
        var nodes = org.mockito.Mockito.mock(WorkflowNodeRepository.class);
        var transitions = org.mockito.Mockito.mock(WorkflowTransitionRepository.class);
        var executions = org.mockito.Mockito.mock(WorkflowNodeExecutionRepository.class);
        var ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
        var security = org.mockito.Mockito.mock(CurrentSecurityContext.class);
        LocalDateTime now = LocalDateTime.now();
        WorkflowDefinition definition = new WorkflowDefinition(1L, "CONTRACT", "Contract",
                "CONTRACT", 100L, 30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 0);
        WorkflowVersion workflowVersion = new WorkflowVersion(2L, 1L, 1,
                WorkflowVersion.Status.PUBLISHED, "1.0", "hash", null,
                now, null, 9L, now, null, 0);
        WorkflowInstance instance = WorkflowInstance.running(10L, "WFI-10", definition,
                workflowVersion, "CONTRACT", "1", "CONTRACT:1", 100L,
                null, null, 1, 9L, 30L, 3L, null, "idem", "request", "trace", now);
        WorkflowNode node = new WorkflowNode(3L, 2L, "REVIEW", "Review",
                WorkflowNode.NodeType.APPROVAL, 1,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                WorkflowNode.ApprovalMode.SINGLE, null,
                WorkflowNode.AssignmentRuleType.USER, "{}", null, null,
                null, false, true, 0);
        SecurityPrincipal principal = new SecurityPrincipal(
                9L, "operator", 30L, Set.of(30L), false, false, 0);
        when(security.principal()).thenReturn(principal);
        WorkflowMultiNodeRuntimeApplicationService service = new WorkflowMultiNodeRuntimeApplicationService(
                definitions, versions, instances, nodes, transitions, executions, ids, security);
        return new Fixture(definitions, versions, instances, nodes, transitions, executions,
                ids, service, definition, workflowVersion, instance, node);
    }

    private record Fixture(
            WorkflowDefinitionRepository definitions,
            WorkflowVersionRepository versions,
            WorkflowInstanceRepository instances,
            WorkflowNodeRepository nodes,
            WorkflowTransitionRepository transitions,
            WorkflowNodeExecutionRepository executions,
            WorkflowIdentityGenerator ids,
            WorkflowMultiNodeRuntimeApplicationService service,
            WorkflowDefinition definition,
            WorkflowVersion workflowVersion,
            WorkflowInstance instance,
            WorkflowNode node) {}

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
