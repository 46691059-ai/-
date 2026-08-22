package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_role_runtime_binding_snapshot")
public class WorkflowRoleRuntimeBindingSnapshotEntity extends WorkflowAuditedEntity {
    private Long approvalId;
    private Long bindingSetId;
    private Long resolverBindingId;
    private Long nodeResolverBindingId;
    private Long instanceId;
    private Long definitionVersionId;
    private Long nodeId;
    private String resolverCode;
    private String resolverVersion;
    private String contractHash;
    private String roleCode;
    private String organizationId;
    private Long directoryRevision;
    private LocalDateTime effectiveAt;
    private String directoryHash;
    private String roleRuleHash;
    private String candidateRuleHash;
    private String sourceEvidenceHash;
    private String bindingHash;
    private String status;
    private String auditInfo;
}
