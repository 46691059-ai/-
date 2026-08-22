package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.application.command.StartWorkflowCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowAssignmentResolverApplicationService;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowRuntimeApplicationService;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowVersionContentHasher;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowResolverVersionBindingApplicationTest {
    @Test
    void linearStartMustFreezeResolverAndUseItForTaskAndSnapshot() {
        WorkflowDefinitionRepository definitions = mock(WorkflowDefinitionRepository.class);
        WorkflowVersionRepository versions = mock(WorkflowVersionRepository.class);
        WorkflowNodeRepository nodes = mock(WorkflowNodeRepository.class);
        WorkflowInstanceRepository instances = mock(WorkflowInstanceRepository.class);
        WorkflowTaskRepository tasks = mock(WorkflowTaskRepository.class);
        WorkflowIdentityGenerator ids = mock(WorkflowIdentityGenerator.class);
        CurrentSecurityContext security = mock(CurrentSecurityContext.class);
        WorkflowNodeExecutionRepository executions = mock(WorkflowNodeExecutionRepository.class);
        WorkflowTransitionRepository transitions = mock(WorkflowTransitionRepository.class);
        WorkflowTaskAssignmentSnapshotRepository snapshots =
                mock(WorkflowTaskAssignmentSnapshotRepository.class);
        LocalDateTime now = LocalDateTime.now();
        WorkflowDefinition definition = new WorkflowDefinition(1L, "LINEAR", "Linear", "TEST",
                100L, 30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 0);
        WorkflowNode node = new WorkflowNode(3L, 2L, "A", "A",
                WorkflowNode.NodeType.APPROVAL, 1,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                WorkflowNode.ApprovalMode.SINGLE, null,
                WorkflowNode.AssignmentRuleType.USER, "{\"userId\":20}",
                null, null, null, true, true, 0);
        WorkflowVersion draftHash = new WorkflowVersion(2L, 1L, 1,
                WorkflowVersion.Status.PUBLISHED, "2.0", "0".repeat(64), null,
                now.minusDays(1), null, 20L, now.minusDays(1), null,
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256, 0);
        String graphHash = new WorkflowVersionContentHasher()
                .hashGraph(definition, draftHash, List.of(node), List.of());
        WorkflowVersion version = new WorkflowVersion(2L, 1L, 1,
                WorkflowVersion.Status.PUBLISHED, "2.0", graphHash, null,
                now.minusDays(1), null, 20L, now.minusDays(1), null,
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256, 0);
        when(security.principal()).thenReturn(new SecurityPrincipal(
                20L, "starter", 30L, Set.of(30L), false, false, 0));
        when(instances.findByEnterpriseIdAndIdempotencyKey(100L, "start-1"))
                .thenReturn(Optional.empty());
        when(definitions.findById(1L)).thenReturn(Optional.of(definition));
        when(versions.findById(2L)).thenReturn(Optional.of(version));
        when(nodes.findByVersionId(2L)).thenReturn(List.of(node));
        when(transitions.findByVersionId(2L)).thenReturn(List.of());
        when(ids.nextId()).thenReturn(10L, 11L, 12L, 13L);
        WorkflowRuntimeApplicationService service = new WorkflowRuntimeApplicationService(
                definitions, versions, nodes, instances, tasks, ids, security, executions,
                transitions, snapshots, new WorkflowAssignmentResolverApplicationService(
                        ResolverRegistry.explicitUserOnly()));

        WorkflowInstance result = service.startWorkflow(new StartWorkflowCommand(
                1L, "TEST", "B-1", "TEST:B-1", 100L, null, null, 1, "{}", "start-1"));

        assertThat(result.requireResolverVersionBinding().resolverCode())
                .isEqualTo(ExplicitUserResolver.CODE);
        assertThat(result.requireResolverVersionBinding().resolverVersion())
                .isEqualTo(ExplicitUserResolver.RESOLVER_VERSION);
        assertThat(result.requireResolverVersionBinding().contractHash())
                .isEqualTo(ExplicitUserResolver.CONTRACT_HASH);
        ArgumentCaptor<WorkflowTask> task = ArgumentCaptor.forClass(WorkflowTask.class);
        ArgumentCaptor<AssignmentSnapshot> snapshot = ArgumentCaptor.forClass(AssignmentSnapshot.class);
        verify(tasks).save(task.capture());
        verify(snapshots).save(snapshot.capture());
        assertThat(task.getValue().assigneeUserId()).isEqualTo(20L);
        assertThat(snapshot.getValue().resolvedUserIds()).containsExactly(20L);
        assertThat(snapshot.getValue().targetSnapshot()).isEqualTo(task.getValue().candidateSnapshot());
    }

    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type);
    }
}
