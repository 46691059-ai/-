package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_task_claim_audit")
public class WorkflowTaskClaimAuditEntity extends WorkflowAuditedEntity {
    private String eventNo;
    private Long taskId;
    private Long candidatePoolId;
    private Long claimId;
    private Long candidateMemberId;
    private Long instanceId;
    private Long nodeExecutionId;
    private Long operatorUserId;
    private Long claimantUserId;
    private String eventType;
    private String result;
    private String reasonCode;
    private String frozenEligibilityHash;
    private Long eligibilityEvidenceId;
    private String eligibilityPersistenceHash;
    private String realtimeEligibilityResult;
    private String rbacResult;
    private String dataScopeResult;
    private String sodResult;
    private String taskStatusBefore;
    private String taskStatusAfter;
    private String poolStatusBefore;
    private String poolStatusAfter;
    private LocalDateTime eventTime;
    private String traceId;
    private String idempotencyKey;
    private String previousEventHash;
    private String eventHash;
}
