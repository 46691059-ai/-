package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_task_claim")
public class WorkflowTaskClaimEntity extends WorkflowAuditedEntity {
    private String claimNo;
    private Long taskId;
    private Long candidatePoolId;
    private Long candidateMemberId;
    private Long instanceId;
    private Long nodeExecutionId;
    private Long candidateUserId;
    private Long operatorUserId;
    private String status;
    private LocalDateTime claimTime;
    private String eligibilitySnapshotHash;
    private Long eligibilityEvidenceId;
    private String eligibilityContractVersion;
    private String admissionId;
    private String realtimeEligibilityResult;
    private String dataScopeResult;
    private String sodResult;
    private String rbacResult;
    private String taskStatusBefore;
    private String taskStatusAfter;
    private String poolStatusBefore;
    private String poolStatusAfter;
    private Integer taskVersionBefore;
    private Integer taskVersionAfter;
    private String idempotencyKey;
    private String traceId;
    private Long activeToken;
}
