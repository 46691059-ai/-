package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_role_binding_candidate_snapshot")
public class WorkflowRoleBindingCandidateSnapshotEntity extends WorkflowAuditedEntity {
    private String snapshotId;
    private Long promotionRowId;
    private String promotionId;
    private String activationId;
    private Long activationDeleteToken;
    private String activationHash;
    private String promotionHash;
    private String promotionEvidenceHash;
    private String promotionReferenceHash;
    private String resolverCode;
    private String resolverVersion;
    private String resolverContractHash;
    private String bindingHash;
    private String candidateHash;
    private String directoryContractHash;
    private Long directoryRevision;
    private String businessScope;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
    private String activationAuditHash;
    private String approvalEvidenceHash;
    private String permissionEvidenceHash;
    private String evidenceSetHash;
    private String snapshotHash;
    private String canonicalVersion;
    /** V2.6.15 future-candidate evidence; legacy rows remain null and are never inferred. */
    private String directoryResultHash;
    private String directoryFenceTokenHash;
    private LocalDateTime directoryFenceExpiresAt;
    private String enterpriseId;
    private Long definitionReleaseId;
    private Long definitionId;
    private Long definitionVersionId;
    private Long nodeId;
    private String nodeBindingHash;
    private String graphHash;
}
