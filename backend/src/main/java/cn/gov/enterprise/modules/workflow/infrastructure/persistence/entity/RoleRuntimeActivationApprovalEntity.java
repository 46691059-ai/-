package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("role_runtime_activation_approval")
public class RoleRuntimeActivationApprovalEntity extends WorkflowAuditedEntity {
    private String activationId;
    private String approverType;
    private String approverId;
    private String decision;
    private String reason;
    private String sourceEvidenceHash;
    private String decisionHash;
    private String activationHash;
    private String contractHash;
    private String bindingHash;
    private LocalDateTime decisionTime;
}
