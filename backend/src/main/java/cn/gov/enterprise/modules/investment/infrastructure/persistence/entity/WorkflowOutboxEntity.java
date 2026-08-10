package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("investment_workflow_outbox")
public class WorkflowOutboxEntity extends BaseIdEntity {
    private Long decisionId; private Long bindingId; private String eventType; private String idempotencyKey;
    private String payloadJson; private String payloadHash; private String status; private Integer retryCount;
    private LocalDateTime nextRetryTime; private String lastErrorCode; private LocalDateTime publishedTime; private String traceId;
    private String workerId; private LocalDateTime lockedAt; private LocalDateTime lockUntil;
    private LocalDateTime deadTime; private String deadReason;
}
