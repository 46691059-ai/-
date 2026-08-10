package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import cn.gov.enterprise.modules.investment.application.workflow.WorkflowEvent;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowBindingEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowInboxEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowBindingMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowInboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkflowInboxGapWorker {
    private static final Logger log = LoggerFactory.getLogger(WorkflowInboxGapWorker.class);
    private final WorkflowInboxMapper inboxMapper;
    private final WorkflowBindingMapper bindingMapper;
    private final WorkflowClient client;
    private final WorkflowInboxService inboxService;
    private final ObjectMapper json;
    private final WorkflowReliabilityProperties properties;
    private final WorkflowReliabilityAuditService audit;

    public WorkflowInboxGapWorker(
            WorkflowInboxMapper inboxMapper,
            WorkflowBindingMapper bindingMapper,
            WorkflowClient client,
            WorkflowInboxService inboxService,
            ObjectMapper json,
            WorkflowReliabilityProperties properties,
            WorkflowReliabilityAuditService audit) {
        this.inboxMapper = inboxMapper;
        this.bindingMapper = bindingMapper;
        this.client = client;
        this.inboxService = inboxService;
        this.json = json;
        this.properties = properties;
        this.audit = audit;
    }

    @Scheduled(fixedDelayString = "${integration.workflow.reliability.gap-scan-delay-millis:5000}")
    public void recover() {
        if (!properties.workerEnabled()) return;
        for (WorkflowInboxEntity head : inboxMapper.selectBufferedHeads(50)) {
            try {
                WorkflowBindingEntity binding = bindingMapper.selectById(head.getBindingId());
                if (binding == null) continue;
                long last = binding.getLastEventSequence() == null ? 0 : binding.getLastEventSequence();
                if (head.getEventSequence() == last + 1) {
                    inboxService.reprocessBuffered(head.getId());
                    continue;
                }
                client.events(head.getWorkflowInstanceId(), last + 1, head.getEventSequence() - 1)
                        .stream().sorted(Comparator.comparingLong(WorkflowEvent::sequence))
                        .forEach(this::receiveRecovered);
                inboxService.reprocessBuffered(head.getId());
            } catch (RuntimeException exception) {
                audit.record(new WorkflowReliabilityAuditService.AuditCommand(
                        head.getDecisionId(), head.getBindingId(), null, head.getId(),
                        "INBOX_FAILURE", "SYSTEM", "FAILED", "GAP_RECOVERY_FAILED",
                        exception.getClass().getSimpleName(), null, null, null, head.getTraceId()));
                log.warn("Workflow event gap recovery failed, inboxId={}, reason={}",
                        head.getId(), exception.getClass().getSimpleName());
            }
        }
    }

    private void receiveRecovered(WorkflowEvent event) {
        try {
            String payload = json.writeValueAsString(event);
            inboxService.receive(event, payload, sha256(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot process recovered Workflow event", exception);
        }
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
