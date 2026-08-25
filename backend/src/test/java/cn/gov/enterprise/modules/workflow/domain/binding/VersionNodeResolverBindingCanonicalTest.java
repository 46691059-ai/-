package cn.gov.enterprise.modules.workflow.domain.binding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionNodeResolverBindingCanonicalTest {
    @Test
    void goldenCanonicalAndHashMustRemainFrozen() {
        VersionNodeResolverBindingComputation result =
                VersionNodeResolverBindingCanonical.compute(
                        VersionResolverBindingCanonicalFixtures.first());

        assertThat(result.canonicalVersion())
                .isEqualTo(VersionNodeResolverBinding.SCHEMA_VERSION);
        assertThat(result.canonical())
                .isEqualTo(VersionResolverBindingCanonicalFixtures.BINDING_ONE_CANONICAL);
        assertThat(result.bindingHash())
                .isEqualTo(VersionResolverBindingCanonicalFixtures.BINDING_ONE_HASH);
    }

    @Test
    void nullableOrganizationMustHaveAnExplicitEncodingDistinctFromEmpty() {
        VersionNodeResolverBinding fixed = VersionResolverBindingCanonicalFixtures.first();
        VersionNodeResolverBinding instanceOrganization = new VersionNodeResolverBinding(
                fixed.id(), fixed.definitionId(), fixed.definitionVersionId(), fixed.nodeId(),
                fixed.bindingOrder(), fixed.resolverCode(), fixed.resolverVersion(),
                fixed.resolverContractHash(), fixed.strategyType(), fixed.resolverMode(),
                fixed.targetType(), fixed.roleCode(), OrganizationScopeType.INSTANCE_BUSINESS_ORG,
                null, fixed.effectiveTimePolicy(), fixed.bindingSchemaVersion(),
                fixed.bindingHash(), fixed.version());

        assertThat(VersionNodeResolverBindingCanonical.canonical(instanceOrganization))
                .contains("21:INSTANCE_BUSINESS_ORG|-1:|")
                .doesNotContain("21:INSTANCE_BUSINESS_ORG|0:|");
    }
}
