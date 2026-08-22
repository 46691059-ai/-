package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.ClaimWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.application.service.TaskClaimTransactionService;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidateMemberStatus;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolMember;
import cn.gov.enterprise.modules.workflow.domain.claim.RealtimeEligibilityChecker;
import cn.gov.enterprise.modules.workflow.domain.claim.SegregationOfDutiesPolicy;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaim;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolMemberRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.TaskClaimAuditRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.TaskClaimRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class WorkflowTaskClaimTransactionServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.now().withNano(0);
    private static final String HASH = ResolverContractHash.sha256("claim-fixture").value();
    private final WorkflowTaskRepository tasks = org.mockito.Mockito.mock(WorkflowTaskRepository.class);
    private final CandidatePoolRepository pools = org.mockito.Mockito.mock(CandidatePoolRepository.class);
    private final CandidatePoolMemberRepository members = org.mockito.Mockito.mock(CandidatePoolMemberRepository.class);
    private final WorkflowNodeExecutionRepository executions = org.mockito.Mockito.mock(WorkflowNodeExecutionRepository.class);
    private final WorkflowInstanceRepository instances = org.mockito.Mockito.mock(WorkflowInstanceRepository.class);
    private final TaskClaimRepository claims = org.mockito.Mockito.mock(TaskClaimRepository.class);
    private final TaskClaimAuditRepository audits = org.mockito.Mockito.mock(TaskClaimAuditRepository.class);
    private final RealtimeEligibilityChecker eligibility = org.mockito.Mockito.mock(RealtimeEligibilityChecker.class);
    private final SegregationOfDutiesPolicy sod = org.mockito.Mockito.mock(SegregationOfDutiesPolicy.class);
    private final WorkflowIdentityGenerator ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
    private final TaskClaimTransactionService service = new TaskClaimTransactionService(
            tasks, pools, members, executions, instances, claims, audits, eligibility, sod, ids);

    @BeforeEach
    void fixture() {
        when(tasks.findByIdForUpdate(200L)).thenReturn(Optional.of(task()));
        when(pools.findByTaskIdForUpdate(200L)).thenReturn(Optional.of(pool()));
        when(members.findByPoolIdAndUserIdForUpdate(300L, 21L)).thenReturn(Optional.of(member(21L)));
        when(executions.findByIdForUpdate(600L)).thenReturn(Optional.of(execution(WorkflowNodeExecution.Status.ACTIVE)));
        when(instances.findByIdForUpdate(10L)).thenReturn(Optional.of(instance(WorkflowInstance.Status.RUNNING)));
        when(claims.findByTaskIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(claims.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eligibility.check(any())).thenReturn(new RealtimeEligibilityChecker.EligibilityDecision(
                true, true, true, true, true, "ELIGIBLE", "all=true"));
        when(sod.evaluate(any())).thenReturn(new SegregationOfDutiesPolicy.Decision(
                true, "BASIC", "V1", "ALLOW", "allowed"));
        when(ids.nextId()).thenReturn(900L, 901L);
        when(tasks.claim(any(), any(Integer.class))).thenReturn(true);
        when(pools.claim(any(), any(Integer.class))).thenReturn(true);
    }

    @Test
    void validCandidateClaimMustPersistOneAtomicResult() throws Exception {
        var result = service.claim(200L, command("idem-1"), principal(21L, Set.of(30L)), "trace");
        assertThat(result.assigneeUserId()).isEqualTo(21L);
        assertThat(result.taskStatus()).isEqualTo("CLAIMED");
        assertThat(result.idempotentReplay()).isFalse();
        verify(claims).insert(any()); verify(tasks).claim(any(), any(Integer.class));
        verify(pools).claim(any(), any(Integer.class)); verify(audits).appendSuccess(any(), any());
        Method method = TaskClaimTransactionService.class.getMethod("claim", Long.class,
                ClaimWorkflowTaskCommand.class, SecurityPrincipal.class, String.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void nonCandidateMustFailBeforeRealtimeChecks() {
        when(members.findByPoolIdAndUserIdForUpdate(300L, 21L)).thenReturn(Optional.empty());
        denied("frozen Candidate");
        verify(eligibility, never()).check(any());
    }

    @Test
    void inactiveEmploymentMustFailClosed() {
        when(eligibility.check(any())).thenReturn(new RealtimeEligibilityChecker.EligibilityDecision(
                false, true, false, true, true, "EMPLOYMENT_INACTIVE", "employment=false"));
        denied("EMPLOYMENT_INACTIVE");
    }

    @Test
    void disabledUserMustFailClosed() {
        when(eligibility.check(any())).thenReturn(new RealtimeEligibilityChecker.EligibilityDecision(
                false, false, true, true, true, "USER_INACTIVE", "user=false"));
        denied("USER_INACTIVE");
    }

    @Test
    void dataScopeAndSodMustBothBeEnforced() {
        assertThatThrownBy(() -> service.claim(200L, command("scope"), principal(21L, Set.of()), "trace"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("data scope");
        when(sod.evaluate(any())).thenReturn(new SegregationOfDutiesPolicy.Decision(
                false, "BASIC", "V1", "INITIATOR_CONFLICT", "denied"));
        denied("INITIATOR_CONFLICT");
    }

    @Test
    void directAndLegacyTasksMustNeverClaimOrBackfill() {
        when(tasks.findByIdForUpdate(200L)).thenReturn(Optional.of(directTask(true)), Optional.of(directTask(false)));
        denied("DIRECT_ASSIGNMENT_NOT_CLAIMABLE");
        denied("DIRECT_ASSIGNMENT_NOT_CLAIMABLE");
        verify(pools, never()).claim(any(), any(Integer.class));
    }

    @Test
    void illegalTaskNodeAndInstanceStatesMustFail() {
        when(tasks.findByIdForUpdate(200L)).thenReturn(Optional.of(task().claim(21L, NOW)));
        denied("not claimable");
        when(tasks.findByIdForUpdate(200L)).thenReturn(Optional.of(task()));
        when(executions.findByIdForUpdate(600L)).thenReturn(Optional.of(execution(WorkflowNodeExecution.Status.COMPLETED)));
        denied("not ACTIVE");
        when(executions.findByIdForUpdate(600L)).thenReturn(Optional.of(execution(WorkflowNodeExecution.Status.ACTIVE)));
        when(instances.findByIdForUpdate(10L)).thenReturn(Optional.of(instance(WorkflowInstance.Status.APPROVED)));
        denied("not RUNNING");
    }

    @Test
    void identicalIdempotencyKeyMustReturnOriginalClaim() {
        TaskClaim existing = TaskClaim.claimed(700L, 200L, 300L, 400L, 10L, 600L,
                21L, NOW, "same", "trace", member(21L).eligibilityHash(), "eligible",
                "ALLOW:ORG", "BASIC:V1:ALLOW", 0);
        when(claims.findByTaskIdAndIdempotencyKey(200L, "same")).thenReturn(Optional.of(existing));
        var result = service.claim(200L, command("same"), principal(21L, Set.of(30L)), "trace");
        assertThat(result.claimId()).isEqualTo(700L);
        assertThat(result.idempotentReplay()).isTrue();
        verify(claims, never()).insert(any());
    }

    @Test
    void casConflictMustRollbackTheDeclaredTransaction() {
        when(tasks.claim(any(), any(Integer.class))).thenReturn(false);
        assertThatThrownBy(() -> service.claim(200L, command("cas-conflict"),
                principal(21L, Set.of(30L)), "trace"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("concurrently");
        verify(pools, never()).claim(any(), any(Integer.class));
    }

    @Test
    void twoConcurrentCandidatesMustHaveSingleCasWinner() throws Exception {
        when(members.findByPoolIdAndUserIdForUpdate(300L, 22L)).thenReturn(Optional.of(member(22L)));
        AtomicBoolean winner = new AtomicBoolean();
        when(tasks.claim(any(), any(Integer.class))).thenAnswer(invocation -> winner.compareAndSet(false, true));
        AtomicLong sequence = new AtomicLong(1000);
        when(ids.nextId()).thenAnswer(invocation -> sequence.incrementAndGet());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> a = () -> invokeConcurrent(start, principal(21L, Set.of(30L)), "a");
            Callable<Boolean> b = () -> invokeConcurrent(start, principal(22L, Set.of(30L)), "b");
            var first = executor.submit(a); var second = executor.submit(b); start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
    }

    private boolean invokeConcurrent(CountDownLatch start, SecurityPrincipal principal, String key)
            throws InterruptedException {
        start.await();
        try { service.claim(200L, command(key), principal, "trace-" + key); return true; }
        catch (BusinessException exception) { return false; }
    }

    private void denied(String message) {
        assertThatThrownBy(() -> service.claim(200L, command("idem"),
                principal(21L, Set.of(30L)), "trace"))
                .isInstanceOf(BusinessException.class).hasMessageContaining(message);
        verify(tasks, never()).claim(any(), any(Integer.class));
    }

    private ClaimWorkflowTaskCommand command(String key) { return new ClaimWorkflowTaskCommand(key, null, null); }
    private SecurityPrincipal principal(Long user, Set<Long> orgs) {
        return new SecurityPrincipal(user, "u" + user, 30L, orgs, false, false, 0);
    }
    private WorkflowTask task() {
        return new WorkflowTask(200L, "WFT-200", 10L, 400L, 500L, 600L, "REVIEW", "Review",
                1, "REVIEW:1:1", null, "{}", WorkflowTask.Status.PENDING,
                "APPROVE,REJECT", null, null, null, null, null, 0,
                WorkflowTask.AssignmentMode.CANDIDATE_POOL);
    }
    private WorkflowTask directTask(boolean linear) {
        return new WorkflowTask(200L, "WFT-200", 10L, 400L, 500L, linear ? 600L : null,
                "REVIEW", "Review", 1, "REVIEW:1:1", linear ? 21L : null, "{}",
                WorkflowTask.Status.PENDING, "APPROVE,REJECT", null, null,
                null, null, null, 0, WorkflowTask.AssignmentMode.DIRECT);
    }
    private CandidatePool pool() {
        var poolMembers = List.of(member(21L), member(22L));
        return CandidatePool.created(300L, "WCP-300", 200L, 10L, 400L, 500L, 600L,
                700L, 800L, 900L, 1000L, AssignmentStrategy.Type.ROLE, "ROLE_RESOLVER",
                "ROLE_V1", ResolverContractHash.of(HASH), HASH, NOW.minusDays(1),
                NOW.minusDays(1), NOW.plusDays(1), poolMembers, "fixture").available();
    }
    private CandidatePoolMember member(Long user) {
        String evidence = "eligible:" + user;
        return new CandidatePoolMember(400L + user, 300L, 200L, 10L, user,
                AssignmentStrategy.Type.ROLE, "ROLE:APPROVER", 30L, 40L, 50L,
                evidence, ResolverContractHash.sha256(evidence).value(), user.intValue() - 20,
                NOW.minusDays(1), CandidateMemberStatus.INCLUDED, "fixture", 0);
    }
    private WorkflowNodeExecution execution(WorkflowNodeExecution.Status status) {
        WorkflowNodeExecution active = new WorkflowNodeExecution(600L, "WNE-600", 10L, 400L,
                500L, "REVIEW", "Review", 1, null, null,
                WorkflowNodeExecution.Status.ACTIVE, null, NOW.minusMinutes(2), NOW.minusMinutes(1),
                null, null, null, "trace", 0);
        return status == WorkflowNodeExecution.Status.ACTIVE
                ? active : active.complete(21L, "APPROVED", NOW);
    }
    private WorkflowInstance instance(WorkflowInstance.Status status) {
        WorkflowDefinition definition = new WorkflowDefinition(1L, "TEST", "Test", "TEST",
                100L, 30L, WorkflowDefinition.Status.ACTIVE, 400L, null, 0);
        WorkflowVersion version = new WorkflowVersion(400L, 1L, 1,
                WorkflowVersion.Status.PUBLISHED, "1.0", "hash", null, NOW, null,
                99L, NOW, null, 0);
        WorkflowInstance running = WorkflowInstance.running(10L, "WFI-10", definition, version,
                "TEST", "1", "TEST:1", 100L, null, null, 1, 99L, 30L, 500L,
                null, "start", "request", null, NOW);
        return status == WorkflowInstance.Status.RUNNING ? running : running.approve(NOW);
    }
}
