package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("investment_workflow_reliability_audit")
public class WorkflowReliabilityAuditEntity extends BaseIdEntity {
    private Long decisionId;
    private Long bindingId;
    private Long outboxId;
    private Long inboxId;
    private String auditType;
    private String actionSource;
    private String resultStatus;
    private String reasonCode;
    private String detailSummary;
    private Long operatorId;
    private Long reviewerId;
    private String ticketNo;
    private String traceId;
}
