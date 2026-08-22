package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_role_binding_promotion")
public class WorkflowRoleBindingPromotionEntity extends WorkflowAuditedEntity {
    private String promotionId;
    private String activationId;
    private Long activationDeleteToken;
    private String activationHash;
    private String activationAuditHash;
    private String approvalEvidenceHash;
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
    private String permissionEvidenceHash;
    private String promotionEvidenceHash;
    private String promotionHash;
    private String status;
    private LocalDateTime promotedAt;
    private String promotedBy;
    private String canonicalVersion;
}
