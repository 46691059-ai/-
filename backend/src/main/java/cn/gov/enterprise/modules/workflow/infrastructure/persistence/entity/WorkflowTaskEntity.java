package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_task")
public class WorkflowTaskEntity extends WorkflowAuditedEntity {
    private String taskNo;
    private Long instanceId;
    private Long versionId;
    private Long nodeId;
    private Long nodeExecutionId;
    private String nodeCodeSnapshot;
    private String nodeNameSnapshot;
    private Integer taskRound;
    private String participantKey;
    private Long assigneeUserId;
    private String candidateSnapshot;
    private String assignmentMode;
    private String status;
    private String allowedActions;
    private LocalDateTime claimedTime;
    private LocalDateTime dueTime;
    private Long completedBy;
    private LocalDateTime completedTime;
    private String decisionResult;
}
