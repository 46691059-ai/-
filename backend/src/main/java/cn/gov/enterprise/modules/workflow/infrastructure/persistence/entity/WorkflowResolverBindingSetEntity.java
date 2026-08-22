package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_instance_resolver_binding_set")
public class WorkflowResolverBindingSetEntity extends WorkflowAuditedEntity {
    private Long instanceId;
    private Long definitionId;
    private Long definitionVersionId;
    private String manifestVersion;
    private String manifestHash;
    private Integer bindingCount;
    private String bindingStatus;
    private LocalDateTime frozenTime;
    private String auditInfo;
}
