package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("role_runtime_activation_evidence")
public class RoleRuntimeActivationEvidenceEntity extends WorkflowAuditedEntity {
    private Long approvalId;
    private String activationId;
    private String evidenceType;
    private String evidenceHash;
    private String activationHash;
    private String contractHash;
    private String bindingHash;
    private String canonicalVersion;
}
