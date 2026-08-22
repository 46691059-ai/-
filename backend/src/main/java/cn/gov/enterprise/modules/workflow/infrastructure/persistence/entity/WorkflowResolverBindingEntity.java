package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_instance_resolver_binding")
public class WorkflowResolverBindingEntity extends WorkflowAuditedEntity {
    private Long bindingSetId;
    private Long instanceId;
    private Long definitionVersionId;
    private String resolverCode;
    private String resolverVersion;
    private String strategyType;
    private String resolverMode;
    private String contractHash;
    private String ruleHash;
    private String bindingStatus;
    private LocalDateTime frozenTime;
    private String auditInfo;
}
