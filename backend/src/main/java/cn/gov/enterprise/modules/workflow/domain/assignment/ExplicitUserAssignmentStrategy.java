package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.util.List;
import java.util.Objects;

/** V1 assignment strategy that freezes one explicitly configured user. */
public final class ExplicitUserAssignmentStrategy implements AssignmentStrategy<Long> {
    @Override
    public Type type() {
        return Type.USER;
    }

    @Override
    public AssignmentResult resolve(AssignmentContext context, Long userId) {
        Objects.requireNonNull(context, "context");
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("explicit workflow assignee must be positive");
        }
        String target = "{\"userId\":" + userId + "}";
        String audit = "{\"reasonCode\":\"EXPLICIT_USER\",\"resolvedBy\":\""
                + context.resolvedBy() + "\"}";
        return new AssignmentResult(type(), type(), target, List.of(userId),
                context.resolveTime(), audit);
    }
}
