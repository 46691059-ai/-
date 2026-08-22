package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.util.Objects;

/** Immutable input passed from an assignment strategy to a resolver. */
public record AssignmentResolverContext(
        AssignmentContext assignmentContext, AssignmentResult strategyResult) {

    public AssignmentResolverContext {
        Objects.requireNonNull(assignmentContext, "assignmentContext");
        Objects.requireNonNull(strategyResult, "strategyResult");
        if (!assignmentContext.resolveTime().equals(strategyResult.resolveTime())) {
            throw new IllegalArgumentException("strategy result time must match assignment context");
        }
    }
}
