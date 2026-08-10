package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("investment_workflow_binding")
public class WorkflowBindingEntity extends BaseIdEntity {
    private Long decisionId; private Long snapshotId; private String snapshotHash; private Integer attemptNo;
    private String businessType; private String businessId; private String businessKey; private Long enterpriseId;
    private String workflowInstanceId; private String definitionKey; private Integer definitionVersion;
    private String idempotencyKey; private String requestHash; private String workflowStatus;
    private Long lastEventSequence; private String lastEventId; private LocalDateTime lastSyncedTime;
    private LocalDateTime startedTime; private LocalDateTime completedTime; private String failureCode; private String traceId;
}
