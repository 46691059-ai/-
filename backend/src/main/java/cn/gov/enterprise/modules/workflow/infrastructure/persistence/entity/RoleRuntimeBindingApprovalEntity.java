package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("role_runtime_binding_approval")
public class RoleRuntimeBindingApprovalEntity extends WorkflowAuditedEntity {
    private String proposalHash;
    private String eligibilityHash;
    private String resolverCode;
    private String resolverVersion;
    private String contractHash;
    private String status;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String rejectReason;
    private String auditInfo;
}
