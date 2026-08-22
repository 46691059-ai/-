package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_instance")
public class WorkflowInstanceEntity extends WorkflowAuditedEntity {
    private String instanceNo;
    private Long definitionId;
    private Long versionId;
    private String definitionCodeSnapshot;
    private Integer definitionVersionNo;
    private String definitionContentHashSnapshot;
    private String resolverCode;
    private String resolverVersion;
    private String resolverContractHash;
    private String businessType;
    private String businessId;
    private String businessKey;
    private Long enterpriseId;
    private String snapshotRef;
    private String snapshotHash;
    private Integer attemptNo;
    private Long initiatorUserId;
    private Long initiatorOrgId;
    private Long currentNodeId;
    private String engineMode;
    private String contentHashAlgorithmSnapshot;
    private Long currentNodeExecutionId;
    private String status;
    private String result;
    private String variablesSnapshot;
    private String idempotencyKey;
    private String requestHash;
    private Long eventSequence;
    private String traceId;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private LocalDateTime withdrawnTime;
}
