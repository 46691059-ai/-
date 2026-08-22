package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_task_candidate_pool")
public class WorkflowCandidatePoolEntity extends WorkflowAuditedEntity {
    private String poolNo;
    private Long taskId;
    private Long instanceId;
    private Long versionId;
    private Long nodeId;
    private Long nodeExecutionId;
    private Long bindingSetId;
    private Long resolverBindingId;
    private Long nodeResolverBindingId;
    private Long assignmentSnapshotId;
    private String assignmentMode;
    private String strategyType;
    private String resolverCode;
    private String resolverVersion;
    private String contractHash;
    private String ruleHash;
    private Integer candidateCount;
    private LocalDateTime generatedTime;
    private LocalDateTime effectiveTime;
    private LocalDateTime expiresTime;
    private String poolHash;
    private String status;
    private String auditInfo;
}
