package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverRegistryException;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.role.CandidateResolutionMode;
import cn.gov.enterprise.modules.workflow.domain.role.ResolverBindingProposalSet;
import cn.gov.enterprise.modules.workflow.domain.role.RoleBindingValidationResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateAdapter;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverBindingProposal;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverBindingProvider;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverBindingRequest;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRoleMultiResolverBindingIntegrationTest {
    private static final Instant EFFECTIVE_AT = Instant.parse("2026-08-13T01:02:03.004Z");
    private static final String ROLE = "INVESTMENT_FINANCE_REVIEWER";
    private static final String ORG = "ORG-001";

    @Test
    void providerMustGeneratePreparedNodeLevelRoleProposals() {
        ResolverBindingProposalSet set = provider().propose(10L, 20L,
                List.of(request(102L, ROLE, ORG), request(101L, ROLE, ORG)));

        assertThat(set.definitionId()).isEqualTo(10L);
        assertThat(set.versionId()).isEqualTo(20L);
        assertThat(set.proposals()).extracting(RoleResolverBindingProposal::nodeId)
                .containsExactly(101L, 102L);
        assertThat(set.proposals()).allSatisfy(proposal -> {
            assertThat(proposal.resolverCode()).isEqualTo(RoleDirectoryResolver.CODE);
            assertThat(proposal.resolverVersion()).isEqualTo(RoleDirectoryResolver.VERSION);
            assertThat(proposal.contractHash()).isEqualTo(RoleDirectoryResolver.CONTRACT_HASH);
            assertThat(proposal.candidateMode())
                    .isEqualTo(CandidateResolutionMode.ROLE_POOL_PREVIEW);
        });
        assertThat(set.hasValidHash()).isTrue();
    }

    @Test
    void proposalInputOrderMustNotChangeBindingHash() {
        ResolverBindingProposalSet first = provider().propose(10L, 20L,
                List.of(request(101L, ROLE, ORG), request(102L, ROLE, ORG)));
        ResolverBindingProposalSet reversed = provider().propose(10L, 20L,
                List.of(request(102L, ROLE, ORG), request(101L, ROLE, ORG)));
        assertThat(reversed.bindingHash()).isEqualTo(first.bindingHash());
    }

    @Test
    void roleOrganizationAndContractChangesMustAffectOrInvalidateBinding() {
        ResolverBindingProposalSet base = provider().propose(10L, 20L,
                List.of(request(101L, ROLE, ORG)));
        ResolverBindingProposalSet roleChanged = provider().propose(10L, 20L,
                List.of(request(101L, "INVESTMENT_LEGAL_REVIEWER", ORG)));
        ResolverBindingProposalSet orgChanged = provider().propose(10L, 20L,
                List.of(request(101L, ROLE, "ORG-002")));
        assertThat(roleChanged.bindingHash()).isNotEqualTo(base.bindingHash());
        assertThat(orgChanged.bindingHash()).isNotEqualTo(base.bindingHash());

        RoleResolverBindingProposal invalid = new RoleResolverBindingProposal(101L,
                RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                ResolverContractHash.of("0".repeat(64)), ROLE, ORG, EFFECTIVE_AT,
                CandidateResolutionMode.ROLE_POOL_PREVIEW);
        ResolverBindingProposalSet invalidSet = ResolverBindingProposalSet.freeze(
                10L, 20L, List.of(invalid));
        assertThat(invalidSet.bindingHash()).isNotEqualTo(base.bindingHash());
        assertThat(provider().validate(invalidSet).status())
                .isEqualTo(RoleBindingValidationResult.Status.INVALID_CONTRACT);
    }

    @Test
    void registryMustExposePreparedMetadataButBlockRoleExecution() {
        ResolverRegistry registry = registry();
        assertThat(registry.requireDescriptor(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION).status()).isEqualTo(ResolverStatus.PREPARED);
        assertThat(registry.requireDescriptor(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION, ResolverStatus.PREPARED,
                RoleDirectoryResolver.CONTRACT_HASH))
                .isEqualTo(RoleDirectoryResolver.PREPARED_DESCRIPTOR);

        assertThatThrownBy(() -> registry.require(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION, RoleDirectoryResolver.CONTRACT_HASH))
                .isInstanceOf(AssignmentResolverRegistryException.class)
                .extracting(error -> ((AssignmentResolverRegistryException) error).reason())
                .isEqualTo(AssignmentResolverRegistryException.Reason.DISABLED);
    }

    @Test
    void roleProposalProviderMustHaveNoRuntimePersistenceOrTaskCreationCapability() {
        assertThat(List.of(RoleResolverBindingProvider.class.getDeclaredFields()))
                .extracting(Field::getType)
                .containsExactly(ResolverRegistry.class);
        assertThat(List.of(RoleResolverBindingProvider.class.getDeclaredMethods()))
                .filteredOn(method -> !method.isSynthetic())
                .extracting(method -> method.getName())
                .containsOnly("propose", "validate");
        assertThat(RoleResolverBindingProposal.class)
                .isNotEqualTo(cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding.class);
    }

    @Test
    void explicitUserResolverMustRemainTheOnlyExecutableAssignmentResolver() {
        ResolverRegistry registry = registry();
        assertThat(registry.require(ExplicitUserResolver.CODE))
                .isInstanceOf(ExplicitUserResolver.class);
        assertThat(registry.require(ExplicitUserResolver.CODE).descriptor().status())
                .isEqualTo(ResolverStatus.ACTIVE);
    }

    private static RoleResolverBindingProvider provider() {
        return new RoleResolverBindingProvider(registry());
    }

    private static ResolverRegistry registry() {
        ExplicitUserResolver explicit = new ExplicitUserResolver();
        return new ResolverRegistry(List.of(explicit.descriptor(),
                RoleDirectoryResolver.PREPARED_DESCRIPTOR,
                RoleCandidateAdapter.PREPARED_DESCRIPTOR), List.of(explicit));
    }

    private static RoleResolverBindingRequest request(Long nodeId, String role, String org) {
        return new RoleResolverBindingRequest(nodeId, role, org, EFFECTIVE_AT);
    }
}
