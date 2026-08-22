package cn.gov.enterprise.modules.workflow.domain.assignment;

/** Pure domain port that resolves an assignment strategy result into candidates. */
public interface AssignmentResolver {
    AssignmentResolverDescriptor descriptor();

    default AssignmentStrategy.Type supportedType() {
        return descriptor().strategyType();
    }

    default String resolverVersion() {
        return descriptor().version().value();
    }

    CandidatePool resolve(AssignmentResolverContext context);
}
