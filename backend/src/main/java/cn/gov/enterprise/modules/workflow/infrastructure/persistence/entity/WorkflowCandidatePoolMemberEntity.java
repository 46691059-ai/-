package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_task_candidate_member")
public class WorkflowCandidatePoolMemberEntity extends WorkflowAuditedEntity {
    private Long poolId;
    private Long taskId;
    private Long instanceId;
    private Long candidateUserId;
    private String sourceType;
    private String sourceRefSnapshot;
    private Long orgIdSnapshot;
    private Long positionIdSnapshot;
    private Long roleIdSnapshot;
    private String eligibilitySnapshot;
    private String eligibilityHash;
    private Integer sortOrder;
    private LocalDateTime generatedTime;
    private String status;
    private String auditInfo;
}
