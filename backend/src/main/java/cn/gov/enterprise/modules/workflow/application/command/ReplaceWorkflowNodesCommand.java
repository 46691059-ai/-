package cn.gov.enterprise.modules.workflow.application.command;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReplaceWorkflowNodesCommand(@NotEmpty List<@Valid NodeCommand> nodes) {
    public record NodeCommand(
            @NotBlank @Size(max = 100) String nodeCode,
            @NotBlank @Size(max = 200) String nodeName,
            @NotNull WorkflowNode.NodeType nodeType,
            @Positive int nodeOrder,
            @NotNull WorkflowNode.GovernanceNodeType governanceNodeType,
            @NotNull WorkflowNode.ApprovalMode approvalMode,
            Integer approvalThreshold,
            @NotNull WorkflowNode.AssignmentRuleType assignmentRuleType,
            @NotBlank String assignmentRuleConfig,
            String entryConditionConfig,
            String completionConditionConfig,
            @Positive Integer timeoutMinutes,
            boolean withdrawAllowed,
            boolean enabled) {}
}
