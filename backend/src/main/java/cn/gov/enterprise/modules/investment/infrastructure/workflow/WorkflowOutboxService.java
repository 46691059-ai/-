package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import cn.gov.enterprise.modules.investment.application.workflow.WorkflowGateway;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowBindingEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowOutboxEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowBindingMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** Transactional outbox writer and reliable dispatcher used by the scheduled worker. */
@Service
public class WorkflowOutboxService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowOutboxService.class);
    private static final String AFTER_COMMIT_WORKER = "after-commit";

    private final WorkflowOutboxMapper outbox;
    private final WorkflowBindingMapper bindings;
    private final InvestmentIdentityGenerator ids;
    private final ObjectMapper json;
    private final WorkflowGateway gateway;
    private final TransactionTemplate transactions;
    private final WorkflowReliabilityProperties properties;
    private final WorkflowReliabilityAuditService audit;

    @Autowired
    public WorkflowOutboxService(
            WorkflowOutboxMapper outbox,
            WorkflowBindingMapper bindings,
            InvestmentIdentityGenerator ids,
            ObjectMapper json,
            WorkflowGateway gateway,
            org.springframework.transaction.PlatformTransactionManager manager,
            WorkflowReliabilityProperties properties,
            WorkflowReliabilityAuditService audit) {
        this.outbox = outbox;
        this.bindings = bindings;
        this.ids = ids;
        this.json = json;
        this.gateway = gateway;
        this.transactions = new TransactionTemplate(manager);
        this.properties = properties;
        this.audit = audit;
    }

    /** Backward-compatible constructor for isolated unit tests. */
    public WorkflowOutboxService(
            WorkflowOutboxMapper outbox, WorkflowBindingMapper bindings,
            InvestmentIdentityGenerator ids, ObjectMapper json, WorkflowGateway gateway,
            org.springframework.transaction.PlatformTransactionManager manager) {
        this(outbox, bindings, ids, json, gateway, manager,
                new WorkflowReliabilityProperties(false, 100, 4, 8, 60, 30, 1800, 1000), null);
    }

    public void enqueueStart(Long decisionId, Long bindingId, WorkflowGateway.StartCommand command) {
        Long id = write(decisionId, bindingId, "START_WORKFLOW", command.idempotencyKey(), serialize(command));
        afterCommit(id);
    }

    public void enqueueWithdraw(Long decisionId, Long bindingId, String instance, String reason, String key) {
        Long id = write(decisionId, bindingId, "WITHDRAW_WORKFLOW", key,
                serialize(new WithdrawPayload(instance, reason, key)));
        afterCommit(id);
    }

    private Long write(Long decisionId, Long bindingId, String type, String key, String payload) {
        WorkflowOutboxEntity row = new WorkflowOutboxEntity();
        row.setId(ids.nextId());
        row.setDecisionId(decisionId);
        row.setBindingId(bindingId);
        row.setEventType(type);
        row.setIdempotencyKey(key);
        row.setPayloadJson(payload);
        row.setPayloadHash(sha(payload));
        row.setStatus("PENDING");
        row.setRetryCount(0);
        row.setDeleteToken(0L);
        outbox.insert(row);
        return row.getId();
    }

    private void afterCommit(Long id) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatch(id); }
            });
        } else {
            dispatch(id);
        }
    }

    /** Low-latency attempt. The scheduled worker remains the reliability guarantee. */
    public void dispatch(Long id) {
        WorkflowOutboxEntity current = outbox.selectById(id);
        if (current == null || terminal(current.getStatus())) return;
        LocalDateTime now = LocalDateTime.now();
        Integer version = current.getVersion() == null ? 0 : current.getVersion();
        if (outbox.claim(id, version, AFTER_COMMIT_WORKER, now, now.plusSeconds(properties.lockSeconds())) == 1) {
            dispatchClaimed(id, AFTER_COMMIT_WORKER);
        }
    }

    public void dispatchClaimed(Long id, String workerId) {
        WorkflowOutboxEntity row = outbox.selectById(id);
        if (row == null || terminal(row.getStatus()) || !"PROCESSING".equals(row.getStatus())) return;
        if (row.getWorkerId() != null && !row.getWorkerId().equals(workerId)) return;
        try {
            verifyPayload(row);
            if ("START_WORKFLOW".equals(row.getEventType())) {
                WorkflowGateway.StartCommand command = json.readValue(
                        row.getPayloadJson(), WorkflowGateway.StartCommand.class);
                WorkflowGateway.WorkflowInstance result = gateway.start(command);
                if (result == null || result.instanceId() == null || result.instanceId().isBlank()) {
                    throw new NonRetryableWorkflowException("WORKFLOW_RESPONSE_INVALID");
                }
                transactions.executeWithoutResult(status -> {
                    WorkflowBindingEntity binding = new WorkflowBindingEntity();
                    binding.setId(row.getBindingId());
                    binding.setWorkflowInstanceId(result.instanceId());
                    binding.setWorkflowStatus("RUNNING");
                    binding.setStartedTime(result.startedTime() == null ? LocalDateTime.now() : result.startedTime());
                    binding.setLastSyncedTime(LocalDateTime.now());
                    bindings.updateById(binding);
                    markPublished(row);
                });
            } else if ("WITHDRAW_WORKFLOW".equals(row.getEventType())) {
                WithdrawPayload payload = json.readValue(row.getPayloadJson(), WithdrawPayload.class);
                gateway.withdraw(payload.instanceId(), payload.reason(), payload.idempotencyKey());
                transactions.executeWithoutResult(status -> markPublished(row));
            } else {
                throw new NonRetryableWorkflowException("UNSUPPORTED_EVENT_TYPE");
            }
        } catch (Exception exception) {
            handleFailure(row, exception);
        }
    }

    private void verifyPayload(WorkflowOutboxEntity row) {
        if (!MessageDigest.isEqual(
                row.getPayloadHash().getBytes(StandardCharsets.US_ASCII),
                sha(row.getPayloadJson()).getBytes(StandardCharsets.US_ASCII))) {
            throw new NonRetryableWorkflowException("PAYLOAD_HASH_MISMATCH");
        }
    }

    private void handleFailure(WorkflowOutboxEntity row, Exception exception) {
        int attempt = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
        String errorCode = errorCode(exception);
        boolean dead = !retryable(exception) || attempt >= properties.maxRetries();
        transactions.executeWithoutResult(status -> {
            row.setRetryCount(attempt);
            row.setLastErrorCode(errorCode);
            row.setWorkerId(null);
            row.setLockedAt(null);
            row.setLockUntil(null);
            if (dead) {
                row.setStatus("DEAD");
                row.setDeadTime(LocalDateTime.now());
                row.setDeadReason(errorCode);
                row.setNextRetryTime(null);
            } else {
                row.setStatus("FAILED");
                row.setNextRetryTime(LocalDateTime.now().plusSeconds(backoffSeconds(attempt, row.getId())));
            }
            outbox.updateById(row);
            if ("START_WORKFLOW".equals(row.getEventType())) {
                WorkflowBindingEntity binding = new WorkflowBindingEntity();
                binding.setId(row.getBindingId());
                binding.setWorkflowStatus(dead ? "START_FAILED" : "START_RETRYING");
                binding.setFailureCode(errorCode);
                bindings.updateById(binding);
            }
        });
        if (audit != null) {
            audit.record(new WorkflowReliabilityAuditService.AuditCommand(
                    row.getDecisionId(), row.getBindingId(), row.getId(), null,
                    dead ? "DEAD_LETTER" : "AUTO_RETRY", "SYSTEM",
                    dead ? "FAILED" : "SUCCESS", errorCode,
                    "attempt=" + attempt, null, null, null, row.getTraceId()));
        }
        log.warn("Workflow outbox dispatch failed, outboxId={}, type={}, attempt={}, dead={}, reason={}",
                row.getId(), row.getEventType(), attempt, dead, errorCode);
    }

    long backoffSeconds(int attempt, Long id) {
        long exponential = properties.baseRetrySeconds() * (1L << Math.min(Math.max(attempt - 1, 0), 20));
        long capped = Math.min(exponential, properties.maxRetrySeconds());
        long jitterRange = Math.max(1, capped / 5);
        long jitter = Math.floorMod(id == null ? attempt : id, jitterRange + 1);
        return Math.min(properties.maxRetrySeconds(), capped + jitter);
    }

    private void markPublished(WorkflowOutboxEntity row) {
        row.setStatus("PUBLISHED");
        row.setPublishedTime(LocalDateTime.now());
        row.setLastErrorCode(null);
        row.setWorkerId(null);
        row.setLockedAt(null);
        row.setLockUntil(null);
        outbox.updateById(row);
    }

    private static boolean retryable(Exception exception) {
        if (exception instanceof NonRetryableWorkflowException) return false;
        if (exception instanceof ResourceAccessException) return true;
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            return status.value() == 429 || status.is5xxServerError();
        }
        return true;
    }

    private static String errorCode(Exception exception) {
        if (exception instanceof NonRetryableWorkflowException workflowException) {
            return workflowException.getMessage();
        }
        if (exception instanceof RestClientResponseException responseException) {
            return "HTTP_" + responseException.getStatusCode().value();
        }
        String name = exception.getClass().getSimpleName();
        return name.length() <= 100 ? name : name.substring(0, 100);
    }

    private static boolean terminal(String status) {
        return "PUBLISHED".equals(status) || "DEAD".equals(status);
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize workflow outbox", exception);
        }
    }

    private static String sha(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record WithdrawPayload(String instanceId, String reason, String idempotencyKey) {}
    private static final class NonRetryableWorkflowException extends RuntimeException {
        private NonRetryableWorkflowException(String code) { super(code); }
    }
}
