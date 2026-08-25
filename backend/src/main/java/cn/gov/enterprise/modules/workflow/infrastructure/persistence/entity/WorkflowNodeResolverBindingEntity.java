package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_node_resolver_binding_snapshot")
public class WorkflowNodeResolverBindingEntity extends WorkflowAuditedEntity {
    private Long bindingSetId;
    private Long resolverBindingId;
    private Long instanceId;
    private Long definitionVersionId;
    private Long nodeId;
    private String nodeCodeSnapshot;
    private Long versionBindingId;
    private Integer versionBindingOrder;
    private Integer versionBindingSlot;
    private String versionBindingHash;
    private String resolverContractHashSnapshot;
    private String roleCode;
    private String organizationScopeType;
    private Long resolvedOrganizationId;
    private String effectiveTimePolicy;
    private String bindingSchemaVersion;
    private String strategyType;
    private String resolverMode;
    private String targetType;
    private String targetValueSnapshot;
    private String ruleVersion;
    private String ruleSnapshot;
    private String ruleHash;
    private String nodeBindingHash;
    private String bindingStatus;
    private LocalDateTime frozenTime;
    private String auditInfo;
}
