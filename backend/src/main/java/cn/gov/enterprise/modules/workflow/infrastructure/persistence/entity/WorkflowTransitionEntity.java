package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_transition")
public class WorkflowTransitionEntity extends WorkflowAuditedEntity {
    private Long versionId;
    private String transitionCode;
    private String transitionName;
    private Long fromNodeId;
    private Long toNodeId;
    private String triggerType;
    private String routeType;
    private Integer priority;
    private String conditionConfig;
    private Integer enabled;
}
