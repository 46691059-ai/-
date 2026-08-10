package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowReliabilityAuditEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowReliabilityAuditMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkflowReliabilityAuditService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowReliabilityAuditService.class);
    private final WorkflowReliabilityAuditMapper mapper;
    private final InvestmentIdentityGenerator ids;

    public WorkflowReliabilityAuditService(
            WorkflowReliabilityAuditMapper mapper, InvestmentIdentityGenerator ids) {
        this.mapper = mapper;
        this.ids = ids;
    }

    public void record(AuditCommand command) {
        try {
            WorkflowReliabilityAuditEntity row = new WorkflowReliabilityAuditEntity();
            row.setId(ids.nextId());
            row.setDecisionId(command.decisionId());
            row.setBindingId(command.bindingId());
            row.setOutboxId(command.outboxId());
            row.setInboxId(command.inboxId());
            row.setAuditType(command.auditType());
            row.setActionSource(command.actionSource());
            row.setResultStatus(command.resultStatus());
            row.setReasonCode(command.reasonCode());
            row.setDetailSummary(limit(command.detailSummary(), 500));
            row.setOperatorId(command.operatorId());
            row.setReviewerId(command.reviewerId());
            row.setTicketNo(limit(command.ticketNo(), 100));
            row.setTraceId(limit(command.traceId(), 64));
            row.setDeleteToken(0L);
            mapper.insert(row);
        } catch (RuntimeException exception) {
            log.error("Workflow reliability audit persistence failed: type={}, reason={}",
                    command.auditType(), exception.getClass().getSimpleName());
        }
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public record AuditCommand(
            Long decisionId, Long bindingId, Long outboxId, Long inboxId,
            String auditType, String actionSource, String resultStatus,
            String reasonCode, String detailSummary, Long operatorId,
            Long reviewerId, String ticketNo, String traceId) {}
}
