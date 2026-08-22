package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_task_assignment_snapshot")
public class WorkflowTaskAssignmentSnapshotEntity extends WorkflowAuditedEntity {
    private Long taskId;
    private Long instanceId;
    private Long versionId;
    private Long nodeId;
    private Long nodeExecutionId;
    private String strategyType;
    private String targetType;
    private String targetSnapshot;
    private String resolvedUsers;
    private Integer resolvedUserCount;
    private LocalDateTime resolveTime;
    private String auditInfo;
    private String traceId;
}
