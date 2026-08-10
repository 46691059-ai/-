package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.gov.enterprise.modules.investment.application.workflow.WorkflowEvent;
import cn.gov.enterprise.modules.investment.application.workflow.WorkflowGateway;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentDecisionCase;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentDecisionRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowBindingEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowInboxEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowOutboxEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowBindingMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowInboxMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowOutboxMapper;
import cn.gov.enterprise.security.CurrentSecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.ResourceAccessException;

class WorkflowReliabilityEnhancementTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void callbackSignatureAndNonceMustBothBeValid() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false);
        WorkflowReliabilityAuditService audit = mock(WorkflowReliabilityAuditService.class);
        WorkflowCallbackSecurityService service = new WorkflowCallbackSecurityService(
                new WorkflowProperties("x", "service", "callback", "decision", 1),
                new WorkflowCallbackSecurityProperties("key-1", "secret-1", null, null, 300, 600),
                redis, audit);
        byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Instant.now().toString();
        String hash = WorkflowCallbackSecurityService.sha256(body);
        String canonical = "POST\n/api/internal/investment/workflow/v1/events\n" + hash + "\n"
                + timestamp + "\nnonce-1\nrequest-1\n1.0";
        var headers = new WorkflowCallbackSecurityService.CallbackHeaders(
                "Bearer callback", "key-1", timestamp, "nonce-1", "request-1", "trace",
                "1.0", hash, WorkflowCallbackSecurityService.hmac("secret-1", canonical));

        assertThat(service.verify(body, headers, "POST", "/api/internal/investment/workflow/v1/events"))
                .isEqualTo(hash);
        assertThatThrownBy(() -> service.verify(
                body, headers, "POST", "/api/internal/investment/workflow/v1/events"))
                .isInstanceOf(BadCredentialsException.class).hasMessageContaining("NONCE_REPLAYED");
        verify(audit).record(argThat(command -> "SECURITY_REJECTED".equals(command.auditType())));
    }

    @Test
    void outboxRetriesWithBackoffAndMovesToDeadLetterAtLimit() throws Exception {
        WorkflowOutboxMapper outbox = mock(WorkflowOutboxMapper.class);
        WorkflowBindingMapper bindings = mock(WorkflowBindingMapper.class);
        WorkflowGateway gateway = mock(WorkflowGateway.class);
        when(gateway.start(any())).thenThrow(new ResourceAccessException("down"));
        PlatformTransactionManager manager = transactionManager();
        WorkflowReliabilityAuditService audit = mock(WorkflowReliabilityAuditService.class);
        WorkflowReliabilityProperties properties =
                new WorkflowReliabilityProperties(true, 10, 2, 2, 60, 30, 1800, 1000);
        WorkflowOutboxService service = new WorkflowOutboxService(
                outbox, bindings, mock(InvestmentIdentityGenerator.class), JSON,
                gateway, manager, properties, audit);
        WorkflowGateway.StartCommand command = new WorkflowGateway.StartCommand(
                "INVESTMENT_DECISION", "10", "key", 1L, 2L, "hash", 1,
                "decision", 1, 7L, Map.of(), "idem", "trace");
        WorkflowOutboxEntity row = outboxRow(JSON.writeValueAsString(command));
        when(outbox.selectById(1L)).thenReturn(row);

        service.dispatchClaimed(1L, "worker-1");
        assertThat(row.getStatus()).isEqualTo("FAILED");
        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getNextRetryTime()).isNotNull();

        row.setStatus("PROCESSING"); row.setWorkerId("worker-1");
        service.dispatchClaimed(1L, "worker-1");
        assertThat(row.getStatus()).isEqualTo("DEAD");
        assertThat(row.getRetryCount()).isEqualTo(2);
        assertThat(row.getDeadTime()).isNotNull();
        verify(audit).record(argThat(commandAudit -> "AUTO_RETRY".equals(commandAudit.auditType())));
        verify(audit).record(argThat(commandAudit -> "DEAD_LETTER".equals(commandAudit.auditType())));
    }

    @Test
    void workerClaimsBatchOnceUnderDatabaseLease() {
        WorkflowOutboxMapper mapper = mock(WorkflowOutboxMapper.class);
        WorkflowOutboxEntity candidate = new WorkflowOutboxEntity();
        candidate.setId(1L); candidate.setVersion(0); candidate.setStatus("PENDING");
        when(mapper.selectDispatchCandidatesForUpdate(any(), eq(10))).thenReturn(List.of(candidate));
        when(mapper.claim(eq(1L), eq(0), anyString(), any(), any())).thenReturn(1);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        PlatformTransactionManager manager = transactionManager();
        Executor direct = Runnable::run;
        WorkflowOutboxWorker worker = new WorkflowOutboxWorker(
                mapper, mock(WorkflowOutboxService.class),
                new WorkflowReliabilityProperties(true, 10, 1, 8, 60, 30, 1800, 1000),
                redis, manager, direct);

        assertThat(worker.claimBatch()).hasSize(1);
        assertThat(candidate.getStatus()).isEqualTo("PROCESSING");
        verify(mapper, times(1)).claim(eq(1L), eq(0), anyString(), any(), any());
    }

    @Test
    void outOfOrderEventIsPersistedAndDrainedAfterGapCloses() throws Exception {
        WorkflowInboxMapper inbox = mock(WorkflowInboxMapper.class);
        WorkflowBindingMapper bindings = mock(WorkflowBindingMapper.class);
        InvestmentDecisionRepository decisions = mock(InvestmentDecisionRepository.class);
        WorkflowBindingEntity binding = binding(1L);
        when(bindings.selectByInstanceForUpdate("wf-1")).thenReturn(binding);
        when(decisions.findByIdForUpdate(10L)).thenReturn(Optional.of(decision()));
        InvestmentIdentityGenerator ids = mock(InvestmentIdentityGenerator.class);
        when(ids.nextId()).thenReturn(301L, 302L);
        AtomicReference<WorkflowInboxEntity> buffered = new AtomicReference<>();
        doAnswer(invocation -> {
            WorkflowInboxEntity row = invocation.getArgument(0);
            if ("BUFFERED".equals(row.getProcessStatus())) buffered.set(row);
            return 1;
        }).when(inbox).insert(any(WorkflowInboxEntity.class));
        when(inbox.selectBuffered(eq(12L), eq(100))).thenAnswer(invocation ->
                buffered.get() == null ? List.of() : List.of(buffered.get()));
        WorkflowInboxService service = new WorkflowInboxService(
                inbox, bindings, decisions, ids, JSON,
                mock(WorkflowReliabilityAuditService.class), mock(CurrentSecurityContext.class));

        WorkflowEvent third = event("evt-3", 3, "APPROVAL_APPROVED", "APPROVED");
        String thirdPayload = JSON.writeValueAsString(third);
        assertThat(service.receive(third, thirdPayload, sha(thirdPayload)))
                .isEqualTo(WorkflowInboxService.Result.BUFFERED);
        WorkflowEvent second = event("evt-2", 2, "NODE_ACTIVATED", null);
        String secondPayload = JSON.writeValueAsString(second);
        assertThat(service.receive(second, secondPayload, sha(secondPayload)))
                .isEqualTo(WorkflowInboxService.Result.PROCESSED);

        assertThat(binding.getLastEventSequence()).isEqualTo(3L);
        assertThat(buffered.get().getProcessStatus()).isEqualTo("PROCESSED");
        verify(decisions).updateDecisionState(10L, InvestmentDecisionCase.Status.APPROVED, 11L);
    }

    @Test
    void manualReplayRequiresDedicatedPermission() throws Exception {
        PreAuthorize permission = WorkflowInboxService.class
                .getMethod("manualReplay", Long.class, WorkflowInboxService.ReplayCommand.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).contains("investment:workflow:replay");
    }

    @Test
    void duplicateEventWithDifferentPayloadIsRejectedAndFlaggedForReview() {
        WorkflowInboxMapper inbox = mock(WorkflowInboxMapper.class);
        WorkflowInboxEntity existing = new WorkflowInboxEntity();
        existing.setId(90L); existing.setEventId("evt-1"); existing.setPayloadHash("old-hash");
        when(inbox.selectByEventId("evt-1")).thenReturn(existing);
        WorkflowReliabilityAuditService audit = mock(WorkflowReliabilityAuditService.class);
        WorkflowInboxService service = new WorkflowInboxService(
                inbox, mock(WorkflowBindingMapper.class), mock(InvestmentDecisionRepository.class),
                mock(InvestmentIdentityGenerator.class), JSON, audit, mock(CurrentSecurityContext.class));

        assertThat(service.receive(event("evt-1", 1, "PROCESS_STARTED", null), "{}", "new-hash"))
                .isEqualTo(WorkflowInboxService.Result.REJECTED);
        assertThat(existing.getManualReviewRequired()).isEqualTo(1);
        verify(inbox).updateById(existing);
        verify(audit).record(argThat(command -> "SECURITY_REJECTED".equals(command.auditType())));
    }

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return manager;
    }

    private static WorkflowOutboxEntity outboxRow(String payload) {
        WorkflowOutboxEntity row = new WorkflowOutboxEntity();
        row.setId(1L); row.setDecisionId(10L); row.setBindingId(12L);
        row.setEventType("START_WORKFLOW"); row.setPayloadJson(payload);
        row.setPayloadHash(WorkflowCallbackSecurityService.sha256(payload.getBytes(StandardCharsets.UTF_8)));
        row.setStatus("PROCESSING"); row.setWorkerId("worker-1"); row.setRetryCount(0); row.setVersion(0);
        return row;
    }

    private static WorkflowBindingEntity binding(long sequence) {
        WorkflowBindingEntity row = new WorkflowBindingEntity();
        row.setId(12L); row.setDecisionId(10L); row.setSnapshotId(11L); row.setAttemptNo(1);
        row.setWorkflowInstanceId("wf-1"); row.setWorkflowStatus("RUNNING");
        row.setLastEventSequence(sequence);
        return row;
    }

    private static InvestmentDecisionCase decision() {
        return new InvestmentDecisionCase(10L, 15L, "D-10", "重大投资",
                InvestmentDecisionCase.Status.IN_APPROVAL, 11L, 0);
    }

    private static WorkflowEvent event(String id, long sequence, String type, String result) {
        return new WorkflowEvent(id, sequence, type, "wf-1", 10L, 11L, 1,
                result, "9", LocalDateTime.now(), "trace");
    }

    private static String sha(String payload) {
        return WorkflowCallbackSecurityService.sha256(payload.getBytes(StandardCharsets.UTF_8));
    }
}
