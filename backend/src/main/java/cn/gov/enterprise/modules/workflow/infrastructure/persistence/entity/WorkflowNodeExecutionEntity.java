package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_node_execution")
public class WorkflowNodeExecutionEntity extends WorkflowAuditedEntity {
    private String executionNo;
    private Long instanceId;
    private Long versionId;
    private Long nodeId;
    private String nodeCodeSnapshot;
    private String nodeNameSnapshot;
    private Integer visitNo;
    private Long previousExecutionId;
    private Long sourceTransitionId;
    private String status;
    private String result;
    private LocalDateTime enteredTime;
    private LocalDateTime activatedTime;
    private Long completedBy;
    private LocalDateTime completedTime;
    private String failureCode;
    private String traceId;
}
