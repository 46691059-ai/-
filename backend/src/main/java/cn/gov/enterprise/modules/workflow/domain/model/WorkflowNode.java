package cn.gov.enterprise.modules.workflow.domain.model;

import java.util.Objects;

/** Node snapshot owned by a workflow version. */
public record WorkflowNode(
        Long id, Long versionId, String nodeCode, String nodeName,
        NodeType nodeType, int nodeOrder, GovernanceNodeType governanceNodeType,
        ApprovalMode approvalMode, Integer approvalThreshold,
        AssignmentRuleType assignmentRuleType, String assignmentRuleConfig,
        String entryConditionConfig, String completionConditionConfig,
        Integer timeoutMinutes, boolean withdrawAllowed, boolean enabled, int version) {

    public enum NodeType { APPROVAL, COUNTERSIGN, CONDITION }
    public enum GovernanceNodeType { GENERAL_APPROVAL, PARTY_PRE_STUDY, BOARD_DECISION, MANAGEMENT_DECISION }
    public enum ApprovalMode { SINGLE, ALL, ANY, QUORUM }
    public enum AssignmentRuleType { USER, ORG, POSITION, ORG_POSITION, RULE }

    public WorkflowNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(versionId, "versionId");
        nodeCode = required(nodeCode, "nodeCode", 100);
        nodeName = required(nodeName, "nodeName", 200);
        Objects.requireNonNull(nodeType, "nodeType");
        Objects.requireNonNull(governanceNodeType, "governanceNodeType");
        Objects.requireNonNull(approvalMode, "approvalMode");
        Objects.requireNonNull(assignmentRuleType, "assignmentRuleType");
        assignmentRuleConfig = required(assignmentRuleConfig, "assignmentRuleConfig", 65535);
        if (nodeOrder <= 0) throw new IllegalArgumentException("nodeOrder must be positive");
        if (approvalMode == ApprovalMode.QUORUM) {
            if (approvalThreshold == null || approvalThreshold <= 0 || approvalThreshold > 100) {
                throw new IllegalArgumentException("QUORUM requires threshold in range 1..100");
            }
        } else if (approvalThreshold != null) {
            throw new IllegalArgumentException("approvalThreshold is only valid for QUORUM");
        }
        if (timeoutMinutes != null && timeoutMinutes <= 0) {
            throw new IllegalArgumentException("timeoutMinutes must be positive");
        }
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    public WorkflowNode copyTo(Long newId, Long targetVersionId) {
        return new WorkflowNode(newId, targetVersionId, nodeCode, nodeName, nodeType, nodeOrder,
                governanceNodeType, approvalMode, approvalThreshold, assignmentRuleType,
                assignmentRuleConfig, entryConditionConfig, completionConditionConfig,
                timeoutMinutes, withdrawAllowed, enabled, 0);
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
