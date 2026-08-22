package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_role_realtime_eligibility_event")
public class WorkflowRoleRealtimeEligibilityEventEntity extends WorkflowAuditedEntity {
    private Long evidenceId; private Long sequenceNo; private String eventType; private Long claimId;
    private String reasonCode; private String previousEventHash; private String eventHash;
    private LocalDateTime occurredAt; private String operatorId; private String idempotencyKey;
    private String canonicalVersion;
}
