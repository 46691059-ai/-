package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.*;

import cn.gov.enterprise.modules.workflow.domain.assignment.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowResolverLifecycleCompatibilityTest {
    @Test
    void retiredResolverCannotStartNewInstanceButCanRunExactHistoricalBinding() {
        ExplicitUserResolver implementation = new ExplicitUserResolver();
        AssignmentResolverDescriptor retired = new AssignmentResolverDescriptor(
                implementation.descriptor().code(), implementation.descriptor().version(),
                implementation.descriptor().strategyType(), implementation.descriptor().mode(),
                implementation.descriptor().contractHash(), ResolverStatus.RETIRED, true);
        ResolverRegistry registry = new ResolverRegistry(List.of(retired), List.of(implementation));
        ResolverVersionBinding binding = new ResolverVersionBinding(1L, retired.code(), retired.version(), retired.contractHash());

        assertThatThrownBy(() -> registry.require(binding))
                .isInstanceOf(AssignmentResolverRegistryException.class);
        assertThat(registry.requireForExistingInstance(binding)).isSameAs(implementation);
    }

    @Test
    void historicalLookupNeverFallsBackAcrossVersionOrHash() {
        ResolverRegistry registry = ResolverRegistry.explicitUserOnly();
        assertThatThrownBy(() -> registry.requireForExistingInstance(new ResolverVersionBinding(
                1L, ExplicitUserResolver.CODE, ResolverVersion.of("EXPLICIT_USER_V2"),
                ExplicitUserResolver.CONTRACT_HASH))).isInstanceOf(AssignmentResolverRegistryException.class);
        assertThatThrownBy(() -> registry.requireForExistingInstance(new ResolverVersionBinding(
                1L, ExplicitUserResolver.CODE, ExplicitUserResolver.RESOLVER_VERSION,
                ResolverContractHash.sha256("different"))))
                .isInstanceOf(AssignmentResolverRegistryException.class);
    }
}
