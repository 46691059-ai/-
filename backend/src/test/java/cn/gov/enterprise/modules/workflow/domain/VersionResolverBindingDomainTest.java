package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifest;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifestCanonical;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifestComputation;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBindingCanonical;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBindingComputation;
import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCanonicalHashSupport;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCombinedContentComputation;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCombinedContentHasher;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class VersionResolverBindingDomainTest {
    @Test
    void resolverBindingModelMustMatchFrozenSchemaExactly() {
        assertThat(ResolverBindingModel.values()).containsExactly(
                ResolverBindingModel.LEGACY_USER_ONLY,
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE);
    }

    @Test
    void fixedOrganizationRoleBindingShouldBeValid() {
        VersionNodeResolverBinding binding = binding(1, OrganizationScopeType.FIXED_ORG, 99L);
        assertThat(binding.organizationId()).isEqualTo(99L);
        assertThat(binding.resolverCode().value()).isEqualTo("ROLE_DIRECTORY");
        assertThat(binding.bindingHash()).isEqualTo("b".repeat(64));
    }

    @Test
    void instanceBusinessOrganizationMustNotFreezeAnOrganization() {
        assertThat(binding(1, OrganizationScopeType.INSTANCE_BUSINESS_ORG, null).organizationId())
                .isNull();
        assertThatThrownBy(() -> binding(1, OrganizationScopeType.FIXED_ORG, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FIXED_ORG");
        assertThatThrownBy(() -> binding(1, OrganizationScopeType.INSTANCE_BUSINESS_ORG, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not freeze");
    }

    @Test
    void invalidOrderRoleAndResolverCombinationMustBeRejected() {
        assertThatThrownBy(() -> binding(0, OrganizationScopeType.FIXED_ORG, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bindingOrder");
        assertThatThrownBy(() -> new VersionNodeResolverBinding(
                1L, 2L, 3L, 4L, 1, ResolverCode.of("ROLE_DIRECTORY"),
                ResolverVersion.of("ROLE_DIRECTORY_V1"), ResolverContractHash.of("a".repeat(64)),
                AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL,
                AssignmentStrategy.Type.ROLE, "role_lower", OrganizationScopeType.FIXED_ORG,
                99L, EffectiveTimePolicy.NODE_ACTIVATED_AT,
                VersionNodeResolverBinding.SCHEMA_VERSION, "b".repeat(64), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uppercase stable key");
        assertThatThrownBy(() -> new VersionNodeResolverBinding(
                1L, 2L, 3L, 4L, 1, ResolverCode.of("EXPLICIT_USER"),
                ResolverVersion.of("ROLE_DIRECTORY_V1"), ResolverContractHash.of("a".repeat(64)),
                AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL,
                AssignmentStrategy.Type.ROLE, "ROLE_APPROVER", OrganizationScopeType.FIXED_ORG,
                99L, EffectiveTimePolicy.NODE_ACTIVATED_AT,
                VersionNodeResolverBinding.SCHEMA_VERSION, "b".repeat(64), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }

    @Test
    void manifestAndVersionSnapshotsMustFollowV2621Rules() {
        ResolverBindingManifest manifest = new ResolverBindingManifest(
                1L, 2L, 3L, ResolverBindingManifest.CANONICAL_VERSION,
                1, "c".repeat(64), 9L, LocalDateTime.now());
        assertThat(manifest.bindingCount()).isEqualTo(1);

        WorkflowVersion legacy = WorkflowVersion.draft(3L, 2L, 1, "1.0", null, null);
        assertThat(legacy.resolverBindingModel()).isEqualTo(ResolverBindingModel.LEGACY_USER_ONLY);
        assertThat(legacy.resolverBindingCount()).isZero();
        assertThat(legacy.resolverBindingManifestHash()).isNull();

        WorkflowVersion capable = new WorkflowVersion(
                3L, 2L, 1, WorkflowVersion.Status.PUBLISHED, "2.0", "d".repeat(64),
                null, LocalDateTime.now(), null, 9L, LocalDateTime.now(), null,
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256,
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                "c".repeat(64), 1, ResolverBindingManifest.CANONICAL_VERSION, 0);
        assertThat(capable.resolverBindingCount()).isEqualTo(1);

        assertThatThrownBy(() -> new WorkflowVersion(
                3L, 2L, 1, WorkflowVersion.Status.PUBLISHED, "2.0", "d".repeat(64),
                null, LocalDateTime.now(), null, 9L, LocalDateTime.now(), null,
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256,
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                null, 0, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incomplete");
    }

    @Test
    void newDomainTypesMustRemainFrameworkFree() {
        for (Class<?> type : Arrays.asList(
                ResolverBindingModel.class, OrganizationScopeType.class,
                EffectiveTimePolicy.class, VersionNodeResolverBinding.class,
                ResolverBindingManifest.class, VersionNodeResolverBindingCanonical.class,
                VersionNodeResolverBindingComputation.class,
                ResolverBindingManifestCanonical.class,
                ResolverBindingManifestComputation.class,
                WorkflowCanonicalHashSupport.class,
                WorkflowCombinedContentHasher.class,
                WorkflowCombinedContentComputation.class)) {
            assertThat(type.getDeclaredAnnotations()).isEmpty();
            assertThat(Arrays.stream(type.getDeclaredFields()).map(field -> field.getType().getName()))
                    .noneMatch(name -> name.startsWith("org.springframework")
                            || name.startsWith("com.baomidou")
                            || name.contains("infrastructure.persistence.entity"));
        }
    }

    private VersionNodeResolverBinding binding(
            int order, OrganizationScopeType organizationScopeType, Long organizationId) {
        return new VersionNodeResolverBinding(
                1L, 2L, 3L, 4L, order, ResolverCode.of("ROLE_DIRECTORY"),
                ResolverVersion.of("ROLE_DIRECTORY_V1"), ResolverContractHash.of("a".repeat(64)),
                AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL,
                AssignmentStrategy.Type.ROLE, "ROLE_APPROVER", organizationScopeType,
                organizationId, EffectiveTimePolicy.NODE_ACTIVATED_AT,
                VersionNodeResolverBinding.SCHEMA_VERSION, "b".repeat(64), 0);
    }
}
