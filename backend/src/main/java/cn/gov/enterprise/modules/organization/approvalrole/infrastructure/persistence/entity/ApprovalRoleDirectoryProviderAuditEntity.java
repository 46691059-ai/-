package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("approval_role_directory_provider_audit")
public class ApprovalRoleDirectoryProviderAuditEntity {
    @TableId private String auditId;
    private String auditEventId; private String correlationId; private String requestId;
    private String providerCode; private String providerVersion; private String serviceIdentity;
    private String environmentIdentity; private String callerServiceIdentity;
    private String enterpriseId; private String organizationId; private String roleCode;
    private LocalDateTime effectiveAt; private String contractVersion; private String contractHash;
    private String canonicalVersion; private Long directoryRevision; private String directoryResultHash;
    private Integer candidateCount; private String outcome; private String failureCode;
    private String requestHash; private String evidenceHash; private LocalDateTime startedAt;
    private LocalDateTime completedAt; private LocalDateTime createdAt; private String retentionPolicy;
}
