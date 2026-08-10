package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("investment_workflow_inbox")
public class WorkflowInboxEntity extends BaseIdEntity {
    private String eventId; private String workflowInstanceId; private Long decisionId; private Long bindingId;
    private Long snapshotId; private Integer attemptNo; private Long eventSequence; private String eventType;
    private String payloadHash; private String payloadJson; private String processStatus; private String failureCode;
    private LocalDateTime receivedTime; private LocalDateTime processedTime; private Integer replayCount;
    private LocalDateTime lastReplayTime; private Long lastReplayBy; private Integer manualReviewRequired;
    private String traceId;
}
