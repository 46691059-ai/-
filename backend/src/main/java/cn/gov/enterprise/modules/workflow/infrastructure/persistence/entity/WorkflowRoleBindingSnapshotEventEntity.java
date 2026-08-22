package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_role_binding_snapshot_event")
public class WorkflowRoleBindingSnapshotEventEntity extends WorkflowAuditedEntity {
    private Long snapshotRowId;
    private String snapshotId;
    private String promotionId;
    private String activationId;
    private Long sequenceNo;
    private String fromStatus;
    private String toStatus;
    private String reasonCode;
    private String sourceEvidenceHash;
    private String previousEventHash;
    private String eventHash;
    private LocalDateTime occurredAt;
    private String operatorId;
    private String canonicalVersion;
}
