package cn.gov.enterprise.modules.workflow.domain.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class VersionNodeResolverBindingHashTest {
    @Test
    void semanticAndIdentityDriftMustChangeTheHash() {
        VersionNodeResolverBinding base = VersionResolverBindingCanonicalFixtures.first();
        Set<String> hashes = Set.of(
                hash(base),
                hash(binding(10L, 20L, 30L, 2, "a", "ROLE_APPROVER", 99L)),
                hash(binding(11L, 20L, 30L, 1, "a", "ROLE_APPROVER", 99L)),
                hash(binding(10L, 21L, 30L, 1, "a", "ROLE_APPROVER", 99L)),
                hash(binding(10L, 20L, 31L, 1, "a", "ROLE_APPROVER", 99L)),
                hash(binding(10L, 20L, 30L, 1, "b", "ROLE_APPROVER", 99L)),
                hash(binding(10L, 20L, 30L, 1, "a", "ROLE_REVIEWER", 99L)),
                hash(binding(10L, 20L, 30L, 1, "a", "ROLE_APPROVER", 100L)));

        assertThat(hashes).hasSize(8);

        VersionNodeResolverBinding dynamicOrg = new VersionNodeResolverBinding(
                base.id(), base.definitionId(), base.definitionVersionId(), base.nodeId(),
                base.bindingOrder(), base.resolverCode(), base.resolverVersion(),
                base.resolverContractHash(), base.strategyType(), base.resolverMode(),
                base.targetType(), base.roleCode(), OrganizationScopeType.INSTANCE_BUSINESS_ORG,
                null, base.effectiveTimePolicy(), base.bindingSchemaVersion(),
                base.bindingHash(), base.version());
        assertThat(hash(dynamicOrg)).isNotEqualTo(hash(base));
    }

    @Test
    void technicalPersistenceDriftMustNotChangeTheHash() {
        VersionNodeResolverBinding base = VersionResolverBindingCanonicalFixtures.first();
        VersionNodeResolverBinding technicalDrift = VersionResolverBindingCanonicalFixtures.binding(
                999999L, base.definitionId(), base.definitionVersionId(), base.nodeId(),
                base.bindingOrder(), "a", base.roleCode(), base.organizationId(),
                91, "f".repeat(64));

        assertThat(hash(technicalDrift)).isEqualTo(hash(base));
        assertThat(Set.of(VersionNodeResolverBinding.class.getRecordComponents()).stream()
                .map(component -> component.getName()).collect(Collectors.toSet()))
                .doesNotContain("createdBy", "createdTime", "updatedBy", "updatedTime",
                        "deleted", "deleteToken", "remark");
    }

    @Test
    void currentlyUnsupportedResolverDimensionsMustFailBeforeHashing() {
        VersionNodeResolverBinding base = VersionResolverBindingCanonicalFixtures.first();
        assertThatThrownBy(() -> changedResolver(base, ResolverCode.of("EXPLICIT_USER"),
                base.resolverVersion(), base.strategyType(), base.resolverMode(), base.targetType()))
                .hasMessageContaining("unsupported");
        assertThatThrownBy(() -> changedResolver(base, base.resolverCode(),
                ResolverVersion.of("ROLE_DIRECTORY_V2"), base.strategyType(),
                base.resolverMode(), base.targetType())).hasMessageContaining("unsupported");
        assertThatThrownBy(() -> changedResolver(base, base.resolverCode(),
                base.resolverVersion(), AssignmentStrategy.Type.USER,
                base.resolverMode(), base.targetType())).hasMessageContaining("unsupported");
        assertThatThrownBy(() -> changedResolver(base, base.resolverCode(),
                base.resolverVersion(), base.strategyType(), ResolverMode.DIRECT,
                base.targetType())).hasMessageContaining("unsupported");
        assertThatThrownBy(() -> changedResolver(base, base.resolverCode(),
                base.resolverVersion(), base.strategyType(), base.resolverMode(),
                AssignmentStrategy.Type.USER)).hasMessageContaining("unsupported");
        assertThatThrownBy(() -> new VersionNodeResolverBinding(
                base.id(), base.definitionId(), base.definitionVersionId(), base.nodeId(),
                base.bindingOrder(), base.resolverCode(), base.resolverVersion(),
                base.resolverContractHash(), base.strategyType(), base.resolverMode(),
                base.targetType(), base.roleCode(), base.organizationScopeType(),
                base.organizationId(), base.effectiveTimePolicy(), "UNSUPPORTED_V2",
                base.bindingHash(), base.version())).hasMessageContaining("bindingSchemaVersion");
    }

    private static String hash(VersionNodeResolverBinding binding) {
        return VersionNodeResolverBindingCanonical.compute(binding).bindingHash();
    }

    private static VersionNodeResolverBinding binding(
            Long definitionId, Long versionId, Long nodeId, int order,
            String contract, String role, Long organizationId) {
        return VersionResolverBindingCanonicalFixtures.binding(
                1L, definitionId, versionId, nodeId, order, contract, role,
                organizationId, 0, "0".repeat(64));
    }

    private static VersionNodeResolverBinding changedResolver(
            VersionNodeResolverBinding base, ResolverCode code, ResolverVersion resolverVersion,
            AssignmentStrategy.Type strategy, ResolverMode mode,
            AssignmentStrategy.Type target) {
        return new VersionNodeResolverBinding(
                base.id(), base.definitionId(), base.definitionVersionId(), base.nodeId(),
                base.bindingOrder(), code, resolverVersion, base.resolverContractHash(),
                strategy, mode, target, base.roleCode(), base.organizationScopeType(),
                base.organizationId(), base.effectiveTimePolicy(), base.bindingSchemaVersion(),
                base.bindingHash(), base.version());
    }
}
