package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.role.CandidateResolutionMode;
import cn.gov.enterprise.modules.workflow.domain.role.ResolverBindingProposalSet;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateAdapter;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateContext;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectorySourceType;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverBindingProposal;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverBindingProvider;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverBindingRequest;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeAuditEvent;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeEligibility;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeEligibilityRequest;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeEligibilityValidator;
import cn.gov.enterprise.modules.workflow.domain.role.RuntimeBindingCandidate;
import cn.gov.enterprise.modules.workflow.domain.role.RuntimeEligibilityRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimeEligibilityGovernanceTest {
    private static final Instant NOW = Instant.parse("2026-08-13T01:02:03.004Z");
    private static final String ROLE = "INVESTMENT_FINANCE_REVIEWER";
    private static final String ORG = "ORG-001";

    @Test
    void validPreparedProposalMustProduceReadyEligibilityAndGovernanceCandidate() {
        Fixture fixture = fixture(10);
        RoleRuntimeEligibility eligibility = fixture.validator.validate(fixture.request);

        assertThat(eligibility.status()).isEqualTo(RoleRuntimeEligibility.Status.READY);
        assertThat(eligibility.bindingHash()).isEqualTo(fixture.set.bindingHash());
        RuntimeBindingCandidate candidate = RuntimeBindingCandidate.from(eligibility, NOW);
        assertThat(candidate.proposalHash()).isEqualTo(fixture.set.bindingHash());
        assertThat(candidate.eligibilityStatus()).isEqualTo(RoleRuntimeEligibility.Status.READY);
        assertThat(RoleRuntimeAuditEvent.capture(eligibility, NOW).failedReason()).isEmpty();
    }

    @Test
    void bindingAndCandidateHashDriftMustBeBlocked() {
        Fixture fixture = fixture(10);
        ResolverBindingProposalSet driftedSet = new ResolverBindingProposalSet(
                fixture.set.definitionId(), fixture.set.versionId(), fixture.set.proposals(),
                "0".repeat(64));
        RoleRuntimeEligibility bindingDrift = fixture.validator.validate(
                new RoleRuntimeEligibilityRequest(driftedSet, fixture.proposal,
                        fixture.candidates, 10));
        assertThat(bindingDrift.status()).isEqualTo(RoleRuntimeEligibility.Status.BLOCKED);
        assertThat(bindingDrift.reason()).isEqualTo("BINDING_HASH_MISMATCH");

        RoleCandidateResult candidateDrift = new RoleCandidateResult(
                fixture.candidates.roleCode(), fixture.candidates.organizationId(),
                fixture.candidates.revision(), fixture.candidates.candidateUsers(),
                fixture.candidates.directoryHash(), fixture.candidates.resolverContractHash(),
                "0".repeat(64), fixture.candidates.source(), fixture.candidates.mode());
        RoleRuntimeEligibility hashDrift = fixture.validator.validate(
                new RoleRuntimeEligibilityRequest(fixture.set, fixture.proposal,
                        candidateDrift, 10));
        assertThat(hashDrift.status()).isEqualTo(RoleRuntimeEligibility.Status.BLOCKED);
        assertThat(hashDrift.reason()).isEqualTo("CANDIDATE_CANONICAL_HASH_MISMATCH");
    }

    @Test
    void missingResolverAndContractMismatchMustFailClosedWithoutFallback() {
        Fixture fixture = fixture(10);
        RoleResolverBindingProposal missing = new RoleResolverBindingProposal(100L,
                ResolverCode.of("MISSING_ROLE_RESOLVER"), ResolverVersion.of("MISSING_V1"),
                RoleDirectoryResolver.CONTRACT_HASH, ROLE, ORG, NOW,
                CandidateResolutionMode.ROLE_POOL_PREVIEW);
        ResolverBindingProposalSet missingSet = ResolverBindingProposalSet.freeze(
                1L, 2L, List.of(missing));
        RoleRuntimeEligibility missingResult = fixture.validator.validate(
                new RoleRuntimeEligibilityRequest(missingSet, missing,
                        fixture.candidates, 10));
        assertThat(missingResult.status()).isEqualTo(RoleRuntimeEligibility.Status.BLOCKED);
        assertThat(missingResult.reason()).isEqualTo("RESOLVER_NOT_FOUND");

        RoleResolverBindingProposal wrongContract = new RoleResolverBindingProposal(100L,
                RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                ResolverContractHash.of("0".repeat(64)), ROLE, ORG, NOW,
                CandidateResolutionMode.ROLE_POOL_PREVIEW);
        ResolverBindingProposalSet wrongSet = ResolverBindingProposalSet.freeze(
                1L, 2L, List.of(wrongContract));
        RoleRuntimeEligibility wrongResult = fixture.validator.validate(
                new RoleRuntimeEligibilityRequest(wrongSet, wrongContract,
                        fixture.candidates, 10));
        assertThat(wrongResult.status()).isEqualTo(RoleRuntimeEligibility.Status.BLOCKED);
        assertThat(wrongResult.reason()).isEqualTo("RESOLVER_CONTRACT_HASH_MISMATCH");
    }

    @Test
    void roleOrganizationAndCandidateLimitMustBeValidated() {
        assertThatThrownBy(() -> new RoleResolverBindingRequest(100L, "", ORG, NOW))
                .isInstanceOf(cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryException.class);
        assertThatThrownBy(() -> new RoleResolverBindingRequest(100L, ROLE, "", NOW))
                .isInstanceOf(cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryException.class);
        assertThatThrownBy(() -> new RoleResolverBindingRequest(100L, ROLE, ORG, null))
                .isInstanceOf(IllegalArgumentException.class);

        Fixture fixture = fixture(10);
        RoleRuntimeEligibility limited = fixture.validator.validate(
                new RoleRuntimeEligibilityRequest(fixture.set, fixture.proposal,
                        fixture.candidates, 1));
        assertThat(limited.status()).isEqualTo(RoleRuntimeEligibility.Status.BLOCKED);
        assertThat(limited.reason()).isEqualTo("CANDIDATE_LIMIT_EXCEEDED");
    }

    @Test
    void preparedAndActiveDescriptorsMayBeInspectedButDisabledChecksAreBlocked() {
        ResolverRegistry resolverRegistry = resolverRegistry();
        RuntimeEligibilityRegistry governance = new RuntimeEligibilityRegistry(
                resolverRegistry, Set.of());
        assertThat(governance.inspect(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION, RoleDirectoryResolver.CONTRACT_HASH).ready()).isTrue();
        assertThat(governance.inspect(ExplicitUserResolver.CODE,
                ExplicitUserResolver.RESOLVER_VERSION,
                ExplicitUserResolver.CONTRACT_HASH).ready()).isTrue();

        RuntimeEligibilityRegistry disabled = new RuntimeEligibilityRegistry(
                resolverRegistry, Set.of(RoleDirectoryResolver.CODE.value()));
        assertThat(disabled.inspect(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION, RoleDirectoryResolver.CONTRACT_HASH).status())
                .isEqualTo(RoleRuntimeEligibility.Status.BLOCKED);
    }

    @Test
    void runtimeHashMustChangeForEveryGovernedRuntimeFactor() {
        Fixture fixture = fixture(10);
        RoleRuntimeEligibility base = fixture.validator.validate(fixture.request);
        String baseHash = fixture.validator.runtimeHash(base, fixture.candidates, 10);
        assertThat(fixture.validator.runtimeHash(base, fixture.candidates, 11))
                .isNotEqualTo(baseHash);

        RoleRuntimeEligibility roleChanged = eligibility(base, ROLE + "_ALT", ORG,
                base.contractHash(), base.bindingHash());
        RoleRuntimeEligibility orgChanged = eligibility(base, ROLE, "ORG-002",
                base.contractHash(), base.bindingHash());
        RoleRuntimeEligibility contractChanged = eligibility(base, ROLE, ORG,
                ResolverContractHash.of("0".repeat(64)), base.bindingHash());
        RoleRuntimeEligibility bindingChanged = eligibility(base, ROLE, ORG,
                base.contractHash(), "0".repeat(64));
        assertThat(List.of(
                fixture.validator.runtimeHash(roleChanged, fixture.candidates, 10),
                fixture.validator.runtimeHash(orgChanged, fixture.candidates, 10),
                fixture.validator.runtimeHash(contractChanged, fixture.candidates, 10),
                fixture.validator.runtimeHash(bindingChanged, fixture.candidates, 10)))
                .doesNotContain(baseHash);
    }

    @Test
    void governanceFrameworkMustHaveNoTaskPoolPersistenceOrRuntimeExecutionDependency() {
        assertThat(List.of(RoleRuntimeEligibilityValidator.class.getDeclaredFields()))
                .extracting(Field::getType)
                .containsExactly(RuntimeEligibilityRegistry.class);
        assertThat(List.of(RoleResolverBindingProvider.class.getDeclaredFields()))
                .extracting(Field::getType)
                .containsExactly(ResolverRegistry.class);
        assertThatThrownBy(() -> resolverRegistry().require(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION, RoleDirectoryResolver.CONTRACT_HASH))
                .isInstanceOf(cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverRegistryException.class);
        assertThat(resolverRegistry().require(ExplicitUserResolver.CODE).descriptor().status())
                .isEqualTo(ResolverStatus.ACTIVE);
    }

    private static Fixture fixture(int limit) {
        ResolverRegistry registry = resolverRegistry();
        RoleResolverBindingProvider provider = new RoleResolverBindingProvider(registry);
        ResolverBindingProposalSet set = provider.propose(1L, 2L,
                List.of(new RoleResolverBindingRequest(100L, ROLE, ORG, NOW)));
        RoleResolverBindingProposal proposal = set.proposals().getFirst();
        RoleDirectoryResult directory = RoleDirectoryResult.complete(ROLE, ORG, NOW, 12,
                List.of(member("USER-001", "ARA-001"), member("USER-002", "ARA-002")),
                RoleDirectoryResolver.CONTRACT_HASH.value(), "FAKE_DIRECTORY");
        RoleCandidateResult candidates = new RoleCandidateAdapter(limit).adapt(
                new RoleCandidateContext(10L, 20L, ROLE, ORG, NOW,
                        new cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding(
                                10L, RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                                RoleDirectoryResolver.CONTRACT_HASH)), directory);
        RoleRuntimeEligibilityValidator validator = new RoleRuntimeEligibilityValidator(
                new RuntimeEligibilityRegistry(registry, Set.of()));
        return new Fixture(set, proposal, candidates, validator,
                new RoleRuntimeEligibilityRequest(set, proposal, candidates, limit));
    }

    private static RoleDirectoryMember member(String user, String assignment) {
        return new RoleDirectoryMember(user, assignment, ROLE, ORG,
                Instant.parse("2026-01-01T00:00:00Z"), null,
                RoleDirectorySourceType.MANUAL_GOVERNANCE, "SRC-" + assignment, 1);
    }

    private static ResolverRegistry resolverRegistry() {
        ExplicitUserResolver explicit = new ExplicitUserResolver();
        return new ResolverRegistry(List.of(explicit.descriptor(),
                RoleDirectoryResolver.PREPARED_DESCRIPTOR,
                RoleCandidateAdapter.PREPARED_DESCRIPTOR), List.of(explicit));
    }

    private static RoleRuntimeEligibility eligibility(RoleRuntimeEligibility base,
            String role, String org, ResolverContractHash contract, String bindingHash) {
        return new RoleRuntimeEligibility(base.resolverCode(), base.resolverVersion(),
                contract, bindingHash, role, org, base.status(), base.reason());
    }

    private record Fixture(
            ResolverBindingProposalSet set, RoleResolverBindingProposal proposal,
            RoleCandidateResult candidates, RoleRuntimeEligibilityValidator validator,
            RoleRuntimeEligibilityRequest request) { }
}
