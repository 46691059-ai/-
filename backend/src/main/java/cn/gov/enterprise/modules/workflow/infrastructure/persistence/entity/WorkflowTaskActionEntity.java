package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_task_action")
public class WorkflowTaskActionEntity extends WorkflowAuditedEntity {
    private String actionNo;
    private Long taskId;
    private Long instanceId;
    private String actionType;
    private Long operatorUserId;
    private Long operatorOrgId;
    private String actionComment;
    private LocalDateTime actionTime;
    private String idempotencyKey;
    private String requestHash;
    private String traceId;
}
