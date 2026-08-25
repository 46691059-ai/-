package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_version_node_resolver_binding")
public class WorkflowVersionNodeResolverBindingEntity extends WorkflowAuditedEntity {
    private Long definitionId;
    private Long definitionVersionId;
    private Long nodeId;
    private Integer bindingOrder;
    private String resolverCode;
    private String resolverVersion;
    private String resolverContractHash;
    private String strategyType;
    private String resolverMode;
    private String targetType;
    private String roleCode;
    private String organizationScopeType;
    private Long organizationId;
    private String effectiveTimePolicy;
    private String bindingSchemaVersion;
    private String bindingHash;
}
