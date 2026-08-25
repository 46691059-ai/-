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
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingManifestRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.VersionNodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionReleaseRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.util.List;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    @ParameterizedTest
    @EnumSource(PublishFailurePoint.class)
    void everyReleasePersistenceFailureMustRollbackTheWholePublication(
            PublishFailurePoint failurePoint) {
        WorkflowDefinitionRepository definitions = org.mockito.Mockito.mock(WorkflowDefinitionRepository.class);
        WorkflowVersionRepository versions = org.mockito.Mockito.mock(WorkflowVersionRepository.class);
        WorkflowNodeRepository nodes = org.mockito.Mockito.mock(WorkflowNodeRepository.class);
        WorkflowIdentityGenerator ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
        WorkflowVersionReleaseRepository releases = org.mockito.Mockito.mock(WorkflowVersionReleaseRepository.class);
        WorkflowTransitionRepository transitions = org.mockito.Mockito.mock(WorkflowTransitionRepository.class);
        VersionNodeResolverBindingRepository bindings =
                org.mockito.Mockito.mock(VersionNodeResolverBindingRepository.class);
        ResolverBindingManifestRepository manifests =
                org.mockito.Mockito.mock(ResolverBindingManifestRepository.class);
        CurrentSecurityContext security = org.mockito.Mockito.mock(CurrentSecurityContext.class);

        WorkflowDefinition definition = new WorkflowDefinition(
                1L, "RC2", "RC2", "TEST", 10L, 8L,
                WorkflowDefinition.Status.ACTIVE, 10L, null, 0);
        WorkflowVersion targetVersion = new WorkflowVersion(
                20L, 1L, 2, WorkflowVersion.Status.DRAFT, "2.0", null, null,
                null, null, null, null, 10L, WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256,
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                null, 0, null, 0);
        WorkflowVersion previousVersion = new WorkflowVersion(
                10L, 1L, 1, WorkflowVersion.Status.PUBLISHED, "1.0", "a".repeat(64), null,
                null, null, 6L, java.time.LocalDateTime.now().minusDays(1), null,
                WorkflowEngineMode.SINGLE_NODE_LEGACY,
                WorkflowContentHashAlgorithm.NODE_V1_SHA256, 0);
        WorkflowNode first = releaseNode(30L, 1);
        WorkflowNode second = releaseNode(40L, 2);
        WorkflowTransition transition = new WorkflowTransition(
                50L, 20L, "A_TO_B", "A to B", 30L, 40L,
                WorkflowTransition.TriggerType.APPROVE, WorkflowTransition.RouteType.DIRECT,
                1, null, true, 0);
        VersionNodeResolverBinding firstBinding = releaseBinding(30L, 1, "ROLE_APPROVER");
        VersionNodeResolverBinding secondBinding = releaseBinding(40L, 1, "ROLE_REVIEWER");

        when(definitions.findByIdForUpdate(1L)).thenReturn(Optional.of(definition));
        when(versions.findByIdForUpdate(20L)).thenReturn(Optional.of(targetVersion));
        when(versions.findByIdForUpdate(10L)).thenReturn(Optional.of(previousVersion));
        when(nodes.findByVersionId(20L)).thenReturn(List.of(first, second));
        when(transitions.findByVersionId(20L)).thenReturn(List.of(transition));
        when(bindings.findByVersionId(20L)).thenReturn(List.of(firstBinding, secondBinding));
        when(versions.prepareResolverBindingSnapshot(any(), org.mockito.ArgumentMatchers.eq(0)))
                .thenReturn(true);
        when(versions.updateState(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> {
                    WorkflowVersion value = invocation.getArgument(0);
                    if (value.id().equals(20L)) {
                        return failurePoint != PublishFailurePoint.TARGET_PUBLICATION;
                    }
                    return failurePoint != PublishFailurePoint.PREVIOUS_RETIREMENT;
                });
        when(definitions.updateCurrentVersion(any(), org.mockito.ArgumentMatchers.eq(0)))
                .thenReturn(failurePoint != PublishFailurePoint.DEFINITION_SWITCH);
        when(ids.nextId()).thenReturn(700L, 701L);
        when(security.principal()).thenReturn(new SecurityPrincipal(
                7L, "publisher", 8L, Set.of(8L), false, false, 0));
        if (failurePoint == PublishFailurePoint.MANIFEST_APPEND) {
            org.mockito.Mockito.doThrow(new IllegalStateException("manifest append failure"))
                    .when(manifests).append(any());
        }
        if (failurePoint == PublishFailurePoint.RELEASE_APPEND) {
            org.mockito.Mockito.doThrow(new IllegalStateException("release append failure"))
                    .when(releases).save(any());
        }

        ExplicitUserResolver explicit = new ExplicitUserResolver();
        ResolverRegistry registry = new ResolverRegistry(
                List.of(explicit.descriptor(), RoleDirectoryResolver.PREPARED_DESCRIPTOR),
                List.of(explicit));
        WorkflowDefinitionApplicationService target = new WorkflowDefinitionApplicationService(
                definitions, versions, nodes, ids, releases, transitions,
                bindings, manifests, registry, security);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        WorkflowDefinitionApplicationService proxy = transactionalProxy(target, transactionManager);

        assertThatThrownBy(() -> proxy.publishVersion(
                1L, 20L, new PublishWorkflowVersionCommand(0, 0, null)))
                .isInstanceOf(RuntimeException.class);
        assertThat(transactionManager.begun).isTrue();
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    private static WorkflowNode releaseNode(Long id, int order) {
        return new WorkflowNode(id, 20L, "NODE_" + id, "Node " + id,
                WorkflowNode.NodeType.APPROVAL, order,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                WorkflowNode.ApprovalMode.SINGLE, null,
                WorkflowNode.AssignmentRuleType.RULE, "{}", null, null,
                null, true, true, 0);
    }

    private static VersionNodeResolverBinding releaseBinding(
            Long nodeId, int order, String roleCode) {
        return new VersionNodeResolverBinding(
                nodeId * 10 + order, 1L, 20L, nodeId, order,
                ResolverCode.of("ROLE_DIRECTORY"), ResolverVersion.of("ROLE_DIRECTORY_V1"),
                ResolverContractHash.of(RoleDirectoryResolver.CONTRACT_HASH.value()),
                AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL,
                AssignmentStrategy.Type.ROLE, roleCode, OrganizationScopeType.FIXED_ORG, 8L,
                EffectiveTimePolicy.NODE_ACTIVATED_AT,
                VersionNodeResolverBinding.SCHEMA_VERSION, "0".repeat(64), 0);
    }

    private static WorkflowDefinitionApplicationService transactionalProxy(
            WorkflowDefinitionApplicationService target,
            RecordingTransactionManager transactionManager) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        return (WorkflowDefinitionApplicationService) factory.getProxy();
    }

    private enum PublishFailurePoint {
        MANIFEST_APPEND,
        TARGET_PUBLICATION,
        PREVIOUS_RETIREMENT,
        DEFINITION_SWITCH,
        RELEASE_APPEND
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
