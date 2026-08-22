package cn.gov.enterprise.modules.workflow.application.vo;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;

/** Public, non-sensitive resolver registration metadata. */
public record AssignmentResolverDetail(
        String resolverCode, String resolverVersion, String strategyType,
        boolean enabled, String selectionMode, String contractHash, String status) {

    public static AssignmentResolverDetail from(AssignmentResolverDescriptor descriptor) {
        return new AssignmentResolverDetail(descriptor.code().value(),
                descriptor.version().value(), descriptor.strategyType().name(),
                descriptor.enabled(), descriptor.mode().name(),
                descriptor.contractHash().value(), descriptor.status().name());
    }
}
