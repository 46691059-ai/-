package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.FreezeCandidatePoolCommand;
import cn.gov.enterprise.modules.workflow.application.service.CandidatePoolApplicationService;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowCandidatePoolDetail;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBindingSet;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolMemberRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.NodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class WorkflowCandidatePoolApplicationServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 11, 0);
    private static final String HASH = ResolverContractHash.sha256("candidate-fixture").value();
    private final CandidatePoolRepository pools = org.mockito.Mockito.mock(CandidatePoolRepository.class);
    private final CandidatePoolMemberRepository members = org.mockito.Mockito.mock(CandidatePoolMemberRepository.class);
    private final WorkflowTaskRepository tasks = org.mockito.Mockito.mock(WorkflowTaskRepository.class);
    private final WorkflowInstanceRepository instances = org.mockito.Mockito.mock(WorkflowInstanceRepository.class);
    private final WorkflowNodeExecutionRepository executions = org.mockito.Mockito.mock(WorkflowNodeExecutionRepository.class);
    private final WorkflowTaskAssignmentSnapshotRepository snapshots =
            org.mockito.Mockito.mock(WorkflowTaskAssignmentSnapshotRepository.class);
    private final ResolverBindingRepository resolvers = org.mockito.Mockito.mock(ResolverBindingRepository.class);
    private final NodeResolverBindingRepository nodeResolvers =
            org.mockito.Mockito.mock(NodeResolverBindingRepository.class);
    private final WorkflowIdentityGenerator ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
    private final CurrentSecurityContext security = org.mockito.Mockito.mock(CurrentSecurityContext.class);
    private final CandidatePoolApplicationService service = new CandidatePoolApplicationService(
            pools, members, tasks, instances, executions, snapshots, resolvers,
            nodeResolvers, ids, security);

    @BeforeEach
    void security() {
        when(security.principal()).thenReturn(new SecurityPrincipal(
                20L, "viewer", 30L, Set.of(30L), false, false, 0));
    }

    @Test
    void freezeMustPersistHeaderAndMembersWithinDeclaredTransaction() throws Exception {
        candidateFixture(execution(10L));
        when(ids.nextId()).thenReturn(100L, 101L, 102L);

        CandidatePool pool = service.freeze(command());

        assertThat(pool.candidateCount()).isEqualTo(2);
        assertThat(pool.status().name()).isEqualTo("AVAILABLE");
        verify(pools).save(pool);
        verify(members).saveAll(pool.members());
        Method method = CandidatePoolApplicationService.class
                .getMethod("freeze", FreezeCandidatePoolCommand.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void memberFailureMustPropagateAcrossTransactionalBoundary() {
        candidateFixture(execution(10L));
        when(ids.nextId()).thenReturn(100L, 101L, 102L);
        doThrow(new BusinessException("B2660", "member failure"))
                .when(members).saveAll(any());

        assertThatThrownBy(() -> service.freeze(command()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("member failure");
        verify(pools).save(any());
    }

    @Test
    void directTaskMustNeverCreateCandidatePool() {
        when(tasks.findById(200L)).thenReturn(Optional.of(directTask(true)));

        assertThatThrownBy(() -> service.freeze(command()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("DIRECT task");
        verify(pools, never()).save(any());
        verify(members, never()).saveAll(any());
    }

    @Test
    void crossInstanceExecutionMustBeRejected() {
        candidateFixture(execution(99L));

        assertThatThrownBy(() -> service.freeze(command()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ownership");
        verify(pools, never()).save(any());
    }

    @Test
    void directAndLegacyQueriesMustNotBackfillCandidatePool() {
        when(tasks.findById(200L)).thenReturn(Optional.of(directTask(true)),
                Optional.of(directTask(false)));
        when(instances.findById(10L)).thenReturn(Optional.of(instance()));
        when(pools.findByTaskId(200L)).thenReturn(Optional.empty());

        assertThat(service.query(200L).source())
                .isEqualTo(WorkflowCandidatePoolDetail.Source.DIRECT_ASSIGNMENT);
        assertThat(service.query(200L).source())
                .isEqualTo(WorkflowCandidatePoolDetail.Source.LEGACY_DIRECT_ASSIGNMENT);
        verify(pools, never()).save(any());
    }

    private void candidateFixture(WorkflowNodeExecution execution) {
        WorkflowTask task = candidateTask();
        when(tasks.findById(200L)).thenReturn(Optional.of(task));
        when(instances.findById(10L)).thenReturn(Optional.of(instance()));
        when(executions.findById(600L)).thenReturn(Optional.of(execution));
        when(pools.existsByTaskId(200L)).thenReturn(false);
        when(snapshots.findByTaskId(200L)).thenReturn(Optional.of(snapshot()));
        when(nodeResolvers.findByInstanceIdAndNodeId(10L, 500L))
                .thenReturn(Optional.of(nodeBinding()));
        when(resolvers.findById(800L)).thenReturn(Optional.of(resolver()));
    }

    private FreezeCandidatePoolCommand command() {
        return new FreezeCandidatePoolCommand(200L, NOW, NOW, NOW.plusDays(1),
                "candidate fixture", List.of(
                new FreezeCandidatePoolCommand.Member(20L, AssignmentStrategy.Type.ROLE,
                        "ROLE:40", 30L, null, 40L, "eligible:20", 1, "member 20"),
                new FreezeCandidatePoolCommand.Member(21L, AssignmentStrategy.Type.ROLE,
                        "ROLE:40", 30L, null, 40L, "eligible:21", 2, "member 21")));
    }

    private WorkflowTask candidateTask() {
        return new WorkflowTask(200L, "WFT-200", 10L, 400L, 500L, 600L,
                "REVIEW", "Review", 1, "REVIEW:1:1", null, "{}",
                WorkflowTask.Status.PENDING, "APPROVE,REJECT", null, null,
                null, null, null, 0, WorkflowTask.AssignmentMode.CANDIDATE_POOL);
    }

    private WorkflowTask directTask(boolean withExecution) {
        return new WorkflowTask(200L, "WFT-200", 10L, 400L, 500L,
                withExecution ? 600L : null, "REVIEW", "Review", 1, "REVIEW:1:1",
                withExecution ? 20L : null, "{}", WorkflowTask.Status.PENDING,
                "APPROVE,REJECT", null, null, null, null, null, 0,
                WorkflowTask.AssignmentMode.DIRECT);
    }

    private WorkflowNodeExecution execution(Long instanceId) {
        return new WorkflowNodeExecution(600L, "WNE-600", instanceId, 400L, 500L,
                "REVIEW", "Review", 1, null, null, WorkflowNodeExecution.Status.ACTIVE,
                null, NOW, NOW, null, null, null, "trace", 0);
    }

    private AssignmentSnapshot snapshot() {
        return new AssignmentSnapshot(1000L, 200L, 10L, 400L, 500L, 600L,
                AssignmentStrategy.Type.ROLE, AssignmentStrategy.Type.ROLE,
                "{\"roleCode\":\"APPROVER\"}", List.of(20L, 21L), NOW,
                "candidate fixture", "trace", 0);
    }

    private WorkflowResolverBinding resolver() {
        return new WorkflowResolverBinding(800L, 700L, 10L, 400L,
                ResolverCode.of("ROLE_RESOLVER"), ResolverVersion.of("ROLE_V1"),
                AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL,
                ResolverContractHash.of(HASH), HASH, WorkflowResolverBindingSet.Status.FROZEN,
                NOW, "candidate fixture", 0);
    }

    private NodeResolverBinding nodeBinding() {
        return new NodeResolverBinding(900L, 700L, 800L, 10L, 400L, 500L,
                "REVIEW", AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL,
                AssignmentStrategy.Type.ROLE, "{\"roleCode\":\"APPROVER\"}",
                "RULE_V1", "{\"roleCode\":\"APPROVER\"}", HASH, HASH,
                WorkflowResolverBindingSet.Status.FROZEN, NOW, "candidate fixture", 0);
    }

    private WorkflowInstance instance() {
        WorkflowDefinition definition = new WorkflowDefinition(1L, "TEST", "Test", "TEST",
                100L, 30L, WorkflowDefinition.Status.ACTIVE, 400L, null, 0);
        WorkflowVersion version = new WorkflowVersion(400L, 1L, 1,
                WorkflowVersion.Status.PUBLISHED, "1.0", "hash", null, NOW, null,
                20L, NOW, null, 0);
        return WorkflowInstance.running(10L, "WFI-10", definition, version, "TEST", "1",
                "TEST:1", 100L, null, null, 1, 20L, 30L, 500L,
                null, "start", "request", null, NOW);
    }
}
