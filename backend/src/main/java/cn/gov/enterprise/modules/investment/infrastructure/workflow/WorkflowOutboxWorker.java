package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowOutboxEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowOutboxMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class WorkflowOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(WorkflowOutboxWorker.class);
    private static final String LOCK_KEY = "enterprise:investment:workflow:outbox:scan";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
            Long.class);

    private final WorkflowOutboxMapper mapper;
    private final WorkflowOutboxService dispatcher;
    private final WorkflowReliabilityProperties properties;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactions;
    private final Executor executor;
    private final String workerId = UUID.randomUUID().toString();

    public WorkflowOutboxWorker(
            WorkflowOutboxMapper mapper,
            WorkflowOutboxService dispatcher,
            WorkflowReliabilityProperties properties,
            StringRedisTemplate redis,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            @Qualifier("workflowOutboxExecutor") Executor executor) {
        this.mapper = mapper;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.redis = redis;
        this.transactions = new TransactionTemplate(transactionManager);
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${integration.workflow.reliability.scan-delay-millis:1000}")
    public void scan() {
        if (!properties.workerEnabled()) return;
        String token = workerId + ':' + UUID.randomUUID();
        boolean locked;
        try {
            locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                    LOCK_KEY, token, Duration.ofSeconds(properties.lockSeconds())));
        } catch (RuntimeException exception) {
            log.error("Workflow outbox scan lock unavailable: reason={}", exception.getClass().getSimpleName());
            return;
        }
        if (!locked) return;
        try {
            for (WorkflowOutboxEntity row : claimBatch()) {
                executor.execute(() -> dispatcher.dispatchClaimed(row.getId(), workerId));
            }
        } finally {
            try {
                redis.execute(RELEASE_SCRIPT, List.of(LOCK_KEY), token);
            } catch (RuntimeException exception) {
                log.warn("Workflow outbox scan lock release failed: reason={}",
                        exception.getClass().getSimpleName());
            }
        }
    }

    List<WorkflowOutboxEntity> claimBatch() {
        List<WorkflowOutboxEntity> claimed = transactions.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            List<WorkflowOutboxEntity> candidates = mapper.selectDispatchCandidatesForUpdate(now, properties.batchSize());
            List<WorkflowOutboxEntity> result = new ArrayList<>();
            for (WorkflowOutboxEntity candidate : candidates) {
                LocalDateTime lockUntil = now.plusSeconds(properties.lockSeconds());
                if (mapper.claim(candidate.getId(), candidate.getVersion(), workerId, now, lockUntil) == 1) {
                    candidate.setStatus("PROCESSING");
                    candidate.setWorkerId(workerId);
                    candidate.setLockedAt(now);
                    candidate.setLockUntil(lockUntil);
                    result.add(candidate);
                }
            }
            return result;
        });
        return claimed == null ? List.of() : claimed;
    }
}
