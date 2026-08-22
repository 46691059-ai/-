package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_node")
public class WorkflowNodeEntity extends WorkflowAuditedEntity {
    private Long versionId;
    private String nodeCode;
    private String nodeName;
    private String nodeType;
    private Integer nodeOrder;
    private String governanceNodeType;
    private String approvalMode;
    private Integer approvalThreshold;
    private String assignmentRuleType;
    private String assignmentRuleConfig;
    private String entryConditionConfig;
    private String completionConditionConfig;
    private Integer timeoutMinutes;
    private Integer withdrawAllowed;
    private Integer enabled;
}
