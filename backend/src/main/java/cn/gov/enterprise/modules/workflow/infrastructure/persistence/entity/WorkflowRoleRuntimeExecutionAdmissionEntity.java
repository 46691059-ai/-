package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_role_runtime_execution_admission")
public class WorkflowRoleRuntimeExecutionAdmissionEntity extends WorkflowAuditedEntity {
    private String admissionId; private String requestId; private String idempotencyKey;
    private Long candidateSnapshotRowId; private String snapshotId; private String promotionId;
    private String activationId; private Long sourceDeleteToken;
    private String activationHash; private String promotionHash; private String bindingHash;
    private String candidateHash; private String executionAdmissionHash;
    private String capabilityEvidenceRootHash; private String persistenceHash;
    private String resolverCode; private String resolverVersion; private String resolverContractHash;
    private Long directoryRevision; private String directoryResultHash; private String directoryFenceTokenHash;
    private LocalDateTime directoryFenceExpiresAt; private LocalDateTime directoryVerifiedAt;
    private String enterpriseId; private String businessScope; private Long definitionReleaseId;
    private Long definitionId; private Long definitionVersionId; private Long nodeId;
    private String nodeBindingHash; private String graphHash; private String decision;
    private String policyVersion; private LocalDateTime effectiveAt; private LocalDateTime admissionExpiresAt;
    private Integer executedCheckCount; private Integer lastCheckSequence;
    private String featureFlagEvidenceHash; private String canaryEvidenceHash; private String killSwitchEvidenceHash;
    private String requestedBy; private String decidedBy; private LocalDateTime decidedAt;
}
