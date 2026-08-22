package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_definition")
public class WorkflowDefinitionEntity extends WorkflowAuditedEntity {
    private String definitionCode;
    private String definitionName;
    private String businessType;
    private Long enterpriseId;
    private Long ownerOrgId;
    private String status;
    private Long currentVersionId;
    private String description;
}
