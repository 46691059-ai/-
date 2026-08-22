package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_role_runtime_execution_admission_event")
public class WorkflowRoleRuntimeExecutionAdmissionEventEntity extends WorkflowAuditedEntity {
    private Long admissionRowId; private String admissionId; private Long candidateSnapshotRowId;
    private Long sequenceNo; private String eventType; private String fromStatus; private String toStatus;
    private String reasonCode; private String sourceEvidenceRootHash; private String sourcePersistenceHash; private String previousEventHash;
    private String eventHash; private LocalDateTime occurredAt; private String operatorId;
    private String operatorRole; private String idempotencyKey; private String canonicalVersion;
}
