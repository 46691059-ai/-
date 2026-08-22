package cn.gov.enterprise.modules.workflow.domain.service;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** WF3.2 supports only a single explicitly configured user; policy resolution belongs to WF3.3. */
public final class ExplicitUserAssignment {
    private static final Pattern JSON_USER_ID = Pattern.compile("\\\"userId\\\"\\s*:\\s*(\\d+)");
    private static final Pattern NUMERIC = Pattern.compile("\\d+");

    public Long resolve(WorkflowNode node) {
        if (node.assignmentRuleType() != WorkflowNode.AssignmentRuleType.USER) {
            throw new IllegalStateException("linear runtime requires an explicit USER assignment");
        }
        String config = node.assignmentRuleConfig().trim();
        String value;
        if (NUMERIC.matcher(config).matches()) {
            value = config;
        } else {
            Matcher matcher = JSON_USER_ID.matcher(config);
            if (!matcher.find()) {
                throw new IllegalStateException("linear runtime requires assignmentRuleConfig.userId");
            }
            value = matcher.group(1);
        }
        long userId;
        try {
            userId = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("explicit workflow assignee is invalid", exception);
        }
        if (userId <= 0) throw new IllegalStateException("explicit workflow assignee must be positive");
        return userId;
    }
}
