package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverRegistryException;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverContext;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowAssignmentResolverRegistryTest {
    private final ExplicitUserResolver explicitUser = new ExplicitUserResolver();

    @Test
    void userResolverMustBeRegisteredAndDiscoverable() {
        ResolverRegistry registry = new ResolverRegistry(List.of(explicitUser));

        assertThat(registry.require(AssignmentStrategy.Type.USER))
                .isSameAs(explicitUser);
        assertThat(registry.descriptors()).containsExactly(explicitUser.descriptor());
    }

    @Test
    void versionMismatchMustBeRejected() {
        ResolverRegistry registry = new ResolverRegistry(List.of(explicitUser));

        assertThatThrownBy(() -> registry.require(
                AssignmentStrategy.Type.USER, ResolverVersion.of("EXPLICIT_USER_V2")))
                .isInstanceOf(AssignmentResolverRegistryException.class)
                .extracting(error -> ((AssignmentResolverRegistryException) error).reason())
                .isEqualTo(AssignmentResolverRegistryException.Reason.VERSION_MISMATCH);
    }

    @Test
    void missingAndDisabledResolversMustBeRejected() {
        ResolverRegistry active = new ResolverRegistry(List.of(explicitUser));
        assertThatThrownBy(() -> active.require(AssignmentStrategy.Type.ROLE))
                .isInstanceOf(AssignmentResolverRegistryException.class)
                .extracting(error -> ((AssignmentResolverRegistryException) error).reason())
                .isEqualTo(AssignmentResolverRegistryException.Reason.NOT_FOUND);

        AssignmentResolverDescriptor disabled = new AssignmentResolverDescriptor(
                ExplicitUserResolver.CODE, ExplicitUserResolver.RESOLVER_VERSION,
                AssignmentStrategy.Type.USER, explicitUser.descriptor().mode(),
                ExplicitUserResolver.CONTRACT_HASH, explicitUser.descriptor().status(), false);
        ResolverRegistry disabledRegistry = new ResolverRegistry(
                List.of(disabled), List.of(explicitUser));
        assertThatThrownBy(() -> disabledRegistry.require(AssignmentStrategy.Type.USER))
                .isInstanceOf(AssignmentResolverRegistryException.class)
                .extracting(error -> ((AssignmentResolverRegistryException) error).reason())
                .isEqualTo(AssignmentResolverRegistryException.Reason.DISABLED);
    }

    @Test
    void nullAndInvalidRegistrationMustBeRejected() {
        ResolverRegistry registry = new ResolverRegistry(List.of(explicitUser));
        assertThatThrownBy(() -> registry.require((AssignmentStrategy.Type) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResolverRegistry(List.of()))
                .isInstanceOf(AssignmentResolverRegistryException.class)
                .extracting(error -> ((AssignmentResolverRegistryException) error).reason())
                .isEqualTo(AssignmentResolverRegistryException.Reason.INVALID_REGISTRATION);
    }

    @Test
    void exactBindingMustRejectMissingVersionAndContractDrift() {
        ResolverRegistry registry = new ResolverRegistry(List.of(explicitUser));

        assertThatThrownBy(() -> registry.require(ExplicitUserResolver.CODE,
                ResolverVersion.of("EXPLICIT_USER_V9"), ExplicitUserResolver.CONTRACT_HASH))
                .isInstanceOf(AssignmentResolverRegistryException.class)
                .extracting(error -> ((AssignmentResolverRegistryException) error).reason())
                .isEqualTo(AssignmentResolverRegistryException.Reason.NOT_FOUND);
        assertThatThrownBy(() -> registry.require(ExplicitUserResolver.CODE,
                ExplicitUserResolver.RESOLVER_VERSION,
                ResolverContractHash.of("0".repeat(64))))
                .isInstanceOf(AssignmentResolverRegistryException.class)
                .extracting(error -> ((AssignmentResolverRegistryException) error).reason())
                .isEqualTo(AssignmentResolverRegistryException.Reason.CONTRACT_HASH_MISMATCH);
    }

    @Test
    void nonActiveVersionMustBeRejected() {
        AssignmentResolverDescriptor deprecated = new AssignmentResolverDescriptor(
                ExplicitUserResolver.CODE, ExplicitUserResolver.RESOLVER_VERSION,
                AssignmentStrategy.Type.USER, ResolverMode.DIRECT,
                ExplicitUserResolver.CONTRACT_HASH, ResolverStatus.DEPRECATED, true);
        ResolverRegistry registry = new ResolverRegistry(
                List.of(deprecated), List.of(explicitUser));

        assertThatThrownBy(() -> registry.require(ExplicitUserResolver.CODE,
                ExplicitUserResolver.RESOLVER_VERSION, ExplicitUserResolver.CONTRACT_HASH))
                .isInstanceOf(AssignmentResolverRegistryException.class)
                .extracting(error -> ((AssignmentResolverRegistryException) error).reason())
                .isEqualTo(AssignmentResolverRegistryException.Reason.STATUS_NOT_ACTIVE);
    }

    @Test
    void newVersionMustNotReplaceAnExistingInstanceBinding() {
        AssignmentResolver v2 = new TestUserResolverV2();
        ResolverRegistry upgradedRegistry = new ResolverRegistry(List.of(explicitUser, v2));
        ResolverVersionBinding oldBinding = ResolverVersionBinding.freeze(
                100L, explicitUser.descriptor());

        assertThat(upgradedRegistry.require(oldBinding)).isSameAs(explicitUser);
        assertThat(upgradedRegistry.require(v2.descriptor().code(),
                v2.descriptor().version(), v2.descriptor().contractHash())).isSameAs(v2);
    }

    private static final class TestUserResolverV2 implements AssignmentResolver {
        private final ExplicitUserResolver delegate = new ExplicitUserResolver();
        private final AssignmentResolverDescriptor descriptor = new AssignmentResolverDescriptor(
                ResolverCode.of("EXPLICIT_USER"), ResolverVersion.of("EXPLICIT_USER_V2"),
                AssignmentStrategy.Type.USER, ResolverMode.DIRECT,
                ResolverContractHash.sha256("EXPLICIT_USER_V2_TEST_CONTRACT"),
                ResolverStatus.ACTIVE, true);

        @Override public AssignmentResolverDescriptor descriptor() { return descriptor; }
        @Override public CandidatePool resolve(AssignmentResolverContext context) {
            return delegate.resolve(context);
        }
    }
}
