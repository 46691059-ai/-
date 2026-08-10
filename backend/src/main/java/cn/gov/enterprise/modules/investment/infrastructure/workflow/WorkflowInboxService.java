package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.workflow.WorkflowEvent;
import cn.gov.enterprise.modules.investment.domain.model.DecisionWorkflowEventPolicy;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentDecisionCase;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentDecisionRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowBindingEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowInboxEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowBindingMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowInboxMapper;
import cn.gov.enterprise.security.CurrentSecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowInboxService {
    private final WorkflowInboxMapper inbox;
    private final WorkflowBindingMapper bindings;
    private final InvestmentDecisionRepository decisions;
    private final InvestmentIdentityGenerator ids;
    private final ObjectMapper json;
    private final WorkflowReliabilityAuditService audit;
    private final CurrentSecurityContext security;
    private final DecisionWorkflowEventPolicy eventPolicy = new DecisionWorkflowEventPolicy();

    @Autowired
    public WorkflowInboxService(
            WorkflowInboxMapper inbox,
            WorkflowBindingMapper bindings,
            InvestmentDecisionRepository decisions,
            InvestmentIdentityGenerator ids,
            ObjectMapper json,
            WorkflowReliabilityAuditService audit,
            CurrentSecurityContext security) {
        this.inbox = inbox;
        this.bindings = bindings;
        this.decisions = decisions;
        this.ids = ids;
        this.json = json;
        this.audit = audit;
        this.security = security;
    }

    /** Backward-compatible constructor for isolated unit tests. */
    public WorkflowInboxService(
            WorkflowInboxMapper inbox, WorkflowBindingMapper bindings,
            InvestmentDecisionRepository decisions, InvestmentIdentityGenerator ids) {
        this(inbox, bindings, decisions, ids, new ObjectMapper().findAndRegisterModules(), null, null);
    }

    @Transactional
    public Result receive(WorkflowEvent event) {
        String payload = serialize(event);
        return receive(event, payload, hash(payload));
    }

    @Transactional
    public Result receive(WorkflowEvent event, String payloadJson, String payloadHash) {
        validate(event);
        WorkflowInboxEntity existing = inbox.selectByEventId(event.eventId());
        if (existing != null) {
            if (!constantEquals(existing.getPayloadHash(), payloadHash)) {
                markSecurityConflict(existing, "EVENT_HASH_CONFLICT");
                return Result.REJECTED;
            }
            return Result.DUPLICATE;
        }

        WorkflowBindingEntity binding = bindings.selectByInstanceForUpdate(event.workflowInstanceId());
        validateBinding(event, binding);
        long lastSequence = sequence(binding);
        if (event.sequence() <= lastSequence) return Result.DUPLICATE;

        WorkflowInboxEntity row = createInboxRow(event, binding.getId(), payloadJson, payloadHash);
        if (event.sequence() > lastSequence + 1) {
            row.setProcessStatus("BUFFERED");
            row.setManualReviewRequired(0);
            inbox.insert(row);
            recordAudit(command(row, "GAP_DETECTED", "SUCCESS", "SEQUENCE_GAP",
                    "expected=" + (lastSequence + 1) + ",actual=" + event.sequence(), null, null, null));
            return Result.BUFFERED;
        }

        Result result = apply(row, event, binding, false);
        if (result == Result.PROCESSED) drainBuffered(binding);
        return result;
    }

    @Transactional
    public Result reprocessBuffered(Long inboxId) {
        WorkflowInboxEntity row = inbox.selectById(inboxId);
        if (row == null || !"BUFFERED".equals(row.getProcessStatus())) return Result.DUPLICATE;
        WorkflowEvent event = deserialize(row.getPayloadJson());
        WorkflowBindingEntity binding = bindings.selectByInstanceForUpdate(row.getWorkflowInstanceId());
        validateBinding(event, binding);
        if (event.sequence() != sequence(binding) + 1) return Result.BUFFERED;
        Result result = apply(row, event, binding, true);
        if (result == Result.PROCESSED) {
            recordAudit(command(row, "GAP_RECOVERED", "SUCCESS", null,
                    "sequence=" + event.sequence(), null, null, null));
            drainBuffered(binding);
        }
        return result;
    }

    @PreAuthorize("hasAuthority('investment:workflow:replay')")
    @Transactional
    public Result manualReplay(Long inboxId, ReplayCommand command) {
        if (command == null || command.reviewerId() == null || command.ticketNo() == null
                || command.ticketNo().isBlank() || command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException("B0691", "人工重放必须提供复核人、工单号和原因");
        }
        if (security == null) throw new BusinessException("B0696", "当前安全上下文不可用");
        Long operatorId = security.userId();
        if (operatorId.equals(command.reviewerId())) {
            throw new BusinessException("B0692", "人工重放申请人和复核人不能相同");
        }
        WorkflowInboxEntity row = inbox.selectById(inboxId);
        if (row == null || !("FAILED".equals(row.getProcessStatus())
                || "BUFFERED".equals(row.getProcessStatus()))) {
            throw new BusinessException("B0693", "当前事件不允许人工重放");
        }
        WorkflowEvent event = deserialize(row.getPayloadJson());
        WorkflowBindingEntity binding = bindings.selectByInstanceForUpdate(row.getWorkflowInstanceId());
        validateBinding(event, binding);
        if (event.sequence() != sequence(binding) + 1) {
            throw new BusinessException("B0694", "事件序号缺口尚未补齐");
        }
        Result result = apply(row, event, binding, true);
        row.setReplayCount((row.getReplayCount() == null ? 0 : row.getReplayCount()) + 1);
        row.setLastReplayTime(LocalDateTime.now());
        row.setLastReplayBy(operatorId);
        inbox.updateById(row);
        recordAudit(command(row, "MANUAL_REPLAY", result == Result.PROCESSED ? "SUCCESS" : "FAILED",
                result.name(), command.reason(), operatorId, command.reviewerId(), command.ticketNo()));
        if (result == Result.PROCESSED) drainBuffered(binding);
        return result;
    }

    private Result apply(WorkflowInboxEntity row, WorkflowEvent event,
            WorkflowBindingEntity binding, boolean existing) {
        DecisionWorkflowEventPolicy.Outcome outcome = eventPolicy.evaluate(event.eventType(), event.result());
        if (outcome.exceptional()) {
            return fail(row, existing, eventErrorCode(event), event);
        }
        InvestmentDecisionCase decision = decisions.findByIdForUpdate(event.decisionId())
                .orElseThrow(() -> new BusinessException("B0684", "投资决策不存在"));
        InvestmentDecisionCase transitioned;
        try {
            transitioned = decision.transitionTo(outcome.decisionStatus());
        } catch (IllegalStateException exception) {
            return fail(row, existing, "ILLEGAL_STATE_TRANSITION", event);
        }
        decisions.updateDecisionState(event.decisionId(), transitioned.status(), event.snapshotId());
        applyBinding(binding, event, outcome.workflowStatus(), null);
        row.setProcessStatus("PROCESSED");
        row.setFailureCode(null);
        row.setManualReviewRequired(0);
        row.setProcessedTime(LocalDateTime.now());
        persist(row, existing);
        return Result.PROCESSED;
    }

    private Result fail(WorkflowInboxEntity row, boolean existing, String code, WorkflowEvent event) {
        row.setProcessStatus("FAILED");
        row.setFailureCode(code);
        row.setManualReviewRequired(1);
        row.setProcessedTime(LocalDateTime.now());
        persist(row, existing);
        recordAudit(command(row, "INBOX_FAILURE", "FAILED", code,
                "eventType=" + event.eventType(), null, null, null));
        return Result.FAILED;
    }

    private void drainBuffered(WorkflowBindingEntity binding) {
        for (WorkflowInboxEntity row : inbox.selectBuffered(binding.getId(), 100)) {
            if (row.getEventSequence() != sequence(binding) + 1) break;
            WorkflowEvent event = deserialize(row.getPayloadJson());
            if (apply(row, event, binding, true) != Result.PROCESSED) break;
            recordAudit(command(row, "GAP_RECOVERED", "SUCCESS", null,
                    "sequence=" + event.sequence(), null, null, null));
        }
    }

    private void applyBinding(WorkflowBindingEntity binding, WorkflowEvent event,
            String workflowStatus, String failureCode) {
        binding.setLastEventSequence(event.sequence());
        binding.setLastEventId(event.eventId());
        binding.setLastSyncedTime(LocalDateTime.now());
        binding.setWorkflowStatus(workflowStatus);
        binding.setFailureCode(failureCode);
        if ("COMPLETED".equals(workflowStatus) || "WITHDRAWN".equals(workflowStatus)
                || "RETURNED".equals(workflowStatus) || "CANCELLED".equals(workflowStatus)
                || "TERMINATED".equals(workflowStatus)) {
            binding.setCompletedTime(LocalDateTime.now());
        }
        bindings.updateById(binding);
    }

    private WorkflowInboxEntity createInboxRow(
            WorkflowEvent event, Long bindingId, String payloadJson, String payloadHash) {
        WorkflowInboxEntity row = new WorkflowInboxEntity();
        row.setId(ids.nextId());
        row.setEventId(event.eventId());
        row.setWorkflowInstanceId(event.workflowInstanceId());
        row.setDecisionId(event.decisionId());
        row.setBindingId(bindingId);
        row.setSnapshotId(event.snapshotId());
        row.setAttemptNo(event.attemptNo());
        row.setEventSequence(event.sequence());
        row.setEventType(event.eventType());
        row.setPayloadHash(payloadHash);
        row.setPayloadJson(payloadJson);
        row.setReceivedTime(LocalDateTime.now());
        row.setReplayCount(0);
        row.setTraceId(event.traceId());
        row.setDeleteToken(0L);
        return row;
    }

    private void markSecurityConflict(WorkflowInboxEntity row, String code) {
        row.setManualReviewRequired(1);
        inbox.updateById(row);
        recordAudit(command(row, "SECURITY_REJECTED", "REJECTED", code,
                "eventId=" + row.getEventId(), null, null, null));
    }

    private static void validateBinding(WorkflowEvent event, WorkflowBindingEntity binding) {
        if (binding == null || !event.decisionId().equals(binding.getDecisionId())
                || !event.snapshotId().equals(binding.getSnapshotId())
                || event.attemptNo() != binding.getAttemptNo()) {
            throw new BusinessException("B0688", "Workflow事件与本地绑定不匹配");
        }
    }

    private static void validate(WorkflowEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()
                || event.eventType() == null || event.eventType().isBlank()
                || event.workflowInstanceId() == null || event.decisionId() == null
                || event.snapshotId() == null || event.sequence() < 1) {
            throw new BusinessException("B0687", "Workflow回调事件不完整");
        }
    }

    private WorkflowEvent deserialize(String payload) {
        try {
            return json.readValue(payload, WorkflowEvent.class);
        } catch (Exception exception) {
            throw new BusinessException("B0695", "Workflow事件载荷无法解析");
        }
    }

    private String serialize(WorkflowEvent event) {
        try {
            return json.writeValueAsString(event);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize Workflow event", exception);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean constantEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private static long sequence(WorkflowBindingEntity binding) {
        return binding.getLastEventSequence() == null ? 0 : binding.getLastEventSequence();
    }

    private static String eventErrorCode(WorkflowEvent event) {
        String value = event.result() == null || event.result().isBlank()
                ? event.eventType() : event.result();
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private void persist(WorkflowInboxEntity row, boolean existing) {
        if (existing) inbox.updateById(row); else inbox.insert(row);
    }

    private static WorkflowReliabilityAuditService.AuditCommand command(
            WorkflowInboxEntity row, String type, String result, String reason,
            String detail, Long operator, Long reviewer, String ticket) {
        return new WorkflowReliabilityAuditService.AuditCommand(
                row.getDecisionId(), row.getBindingId(), null, row.getId(), type,
                operator == null ? "SYSTEM" : "ADMIN", result, reason, detail,
                operator, reviewer, ticket, row.getTraceId());
    }

    private void recordAudit(WorkflowReliabilityAuditService.AuditCommand command) {
        if (audit != null) audit.record(command);
    }

    public record ReplayCommand(Long reviewerId, String ticketNo, String reason) {}
    public enum Result { PROCESSED, DUPLICATE, BUFFERED, FAILED, REJECTED }
}
