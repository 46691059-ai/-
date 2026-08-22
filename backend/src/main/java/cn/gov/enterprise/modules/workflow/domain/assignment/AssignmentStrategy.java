package cn.gov.enterprise.modules.workflow.domain.assignment;

/** Pure domain strategy for resolving a frozen workflow task assignment. */
public interface AssignmentStrategy<T> {
    enum Type { USER, ROLE, POSITION, ORG }

    Type type();

    AssignmentResult resolve(AssignmentContext context, T target);
}
