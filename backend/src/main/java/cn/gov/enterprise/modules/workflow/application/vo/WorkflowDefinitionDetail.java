package cn.gov.enterprise.modules.workflow.application.vo;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import java.util.List;

public record WorkflowDefinitionDetail(
        WorkflowDefinition definition,
        List<VersionDetail> versions) {
    public record VersionDetail(WorkflowVersion version, List<WorkflowNode> nodes) {}
}
