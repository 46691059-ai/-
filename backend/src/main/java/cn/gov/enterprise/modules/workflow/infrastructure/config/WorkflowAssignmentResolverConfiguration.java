package cn.gov.enterprise.modules.workflow.infrastructure.config;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateAdapter;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverBindingProvider;
import cn.gov.enterprise.modules.workflow.domain.role.RuntimeEligibilityRegistry;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeEligibilityValidator;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Startup-only resolver registration. No dynamic registration endpoint is exposed. */
@Configuration
public class WorkflowAssignmentResolverConfiguration {
    @Bean
    AssignmentResolver explicitUserResolver() {
        return new ExplicitUserResolver();
    }

    @Bean
    AssignmentResolverDescriptor roleDirectoryPreparedDescriptor() {
        return RoleDirectoryResolver.PREPARED_DESCRIPTOR;
    }

    @Bean
    AssignmentResolverDescriptor roleCandidateAdapterPreparedDescriptor() {
        return RoleCandidateAdapter.PREPARED_DESCRIPTOR;
    }

    @Bean
    ResolverRegistry workflowResolverRegistry(
            List<AssignmentResolver> resolvers,
            List<AssignmentResolverDescriptor> preparedDescriptors) {
        List<AssignmentResolverDescriptor> descriptors = new ArrayList<>(preparedDescriptors);
        descriptors.addAll(resolvers.stream().map(AssignmentResolver::descriptor).toList());
        if (descriptors.stream().anyMatch(descriptor ->
                (descriptor.code().equals(RoleDirectoryResolver.CODE)
                        || descriptor.code().equals(RoleCandidateAdapter.CODE))
                        && descriptor.status() != cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus.PREPARED)) {
            throw new IllegalStateException("ROLE runtime components must remain PREPARED");
        }
        return new ResolverRegistry(descriptors, resolvers);
    }

    @Bean
    RoleResolverBindingProvider roleResolverBindingProvider(ResolverRegistry resolverRegistry) {
        return new RoleResolverBindingProvider(resolverRegistry);
    }

    @Bean
    RuntimeEligibilityRegistry roleRuntimeEligibilityRegistry(ResolverRegistry resolverRegistry) {
        return new RuntimeEligibilityRegistry(resolverRegistry, Set.of());
    }

    @Bean
    RoleRuntimeEligibilityValidator roleRuntimeEligibilityValidator(
            RuntimeEligibilityRegistry eligibilityRegistry) {
        return new RoleRuntimeEligibilityValidator(eligibilityRegistry);
    }
}
