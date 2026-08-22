package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("role_runtime_activation_request")
public class RoleRuntimeActivationRequestEntity extends WorkflowAuditedEntity {
    private String activationId;
    private String resolverCode;
    private String resolverVersion;
    private String contractHash;
    private String bindingHash;
    private String candidateHash;
    private String directoryContractHash;
    private Long directoryRevision;
    private String activationHash;
    private String approvalEvidenceHash;
    private String businessScope;
    private LocalDateTime effectiveAt;
    private String requestedBy;
    private String status;
}
