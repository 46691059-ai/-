package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.application.service.RoleRuntimeExecutionAdmissionApplicationService;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverContext;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.admission.ExecutableRuntimeBindingCandidate;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeCanaryScope;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeCapabilityReadiness;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionDecision;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionFacts;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionHash;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionPolicy;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionRequest;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionStatus;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionValidator;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionBlockReason;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeFeatureFlagPolicy;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeKillSwitch;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingLifecycleStatus;
import cn.gov.enterprise.modules.workflow.support.FakeRoleRuntimeExecutionCapabilityAdapters.FakeAuditCapabilityAdapter;
import cn.gov.enterprise.modules.workflow.support.FakeRoleRuntimeExecutionCapabilityAdapters.FakeCanaryScopeAdapter;
import cn.gov.enterprise.modules.workflow.support.FakeRoleRuntimeExecutionCapabilityAdapters.FakeDataScopeCapabilityAdapter;
import cn.gov.enterprise.modules.workflow.support.FakeRoleRuntimeExecutionCapabilityAdapters.FakeFeatureFlagCapabilityAdapter;
import cn.gov.enterprise.modules.workflow.support.FakeRoleRuntimeExecutionCapabilityAdapters.FakeKillSwitchCapabilityAdapter;
import cn.gov.enterprise.modules.workflow.support.FakeRoleRuntimeExecutionCapabilityAdapters.FakeProductionDirectoryCapabilityAdapter;
import cn.gov.enterprise.modules.workflow.support.FakeRoleRuntimeExecutionCapabilityAdapters.FakeRealtimeEligibilityCapabilityAdapter;
import cn.gov.enterprise.modules.workflow.support.FakeRoleRuntimeExecutionCapabilityAdapters.FakeSoDCapabilityAdapter;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimeExecutionAdmissionFrameworkTest {
    private static final Instant EFFECTIVE = Instant.parse("2026-08-17T08:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-17T09:00:00Z");
    private static final String ACTIVATION = "a".repeat(64);
    private static final String PROMOTION = "b".repeat(64);
    private static final String BINDING = "c".repeat(64);
    private static final String CANDIDATE = "d".repeat(64);
    private static final String DIRECTORY = "e".repeat(64);

    @Test
    void allTwentyEightChecksReadyMustApproveOnlyAnIsolatedCandidate() {
        Fixture fixture = fixture();
        RoleRuntimeExecutionAdmissionDecision decision = fixture.admit();

        assertThat(decision.status())
                .isEqualTo(RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION);
        assertThat(decision.reason()).isEqualTo(RoleRuntimeExecutionBlockReason.NONE);
        assertThat(decision.evidence().orElseThrow().checks()).hasSize(28);
        assertThat(decision.evidence().orElseThrow().checks())
                .extracting(check -> check.sequence()).containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 28).boxed().toList());
        ExecutableRuntimeBindingCandidate candidate = decision.candidate().orElseThrow();
        assertThat(candidate.runtimeEnabled()).isFalse();
        assertThat(candidate.admissionHash()).matches("[0-9a-f]{64}");
        assertThat(Arrays.stream(ExecutableRuntimeBindingCandidate.class.getDeclaredFields())
                .map(Field::getType).map(Class::getName))
                .noneMatch(name -> name.contains("WorkflowInstance")
                        || name.contains("NodeExecution") || name.contains("WorkflowTask")
                        || name.contains("CandidatePool") || name.contains("Claim"));
    }

    @Test
    void frozenGovernanceEvidenceMustFailClosedInExactOrder() {
        assertBlocked(facts().candidateExists(false).build(),
                RoleRuntimeExecutionBlockReason.CANDIDATE_NOT_FOUND);
        assertBlocked(facts().candidateStatus(RoleRuntimeBindingLifecycleStatus.BLOCKED).build(),
                RoleRuntimeExecutionBlockReason.CANDIDATE_SNAPSHOT_STATUS_INVALID);
        assertBlocked(facts().promotionEvidenceComplete(false).build(),
                RoleRuntimeExecutionBlockReason.PROMOTION_EVIDENCE_MISSING);
        assertBlocked(facts().activationEvidenceComplete(false).build(),
                RoleRuntimeExecutionBlockReason.ACTIVATION_EVIDENCE_MISSING);
        assertBlocked(facts().activationRevoked(true).build(),
                RoleRuntimeExecutionBlockReason.ACTIVATION_REVOKED);
        assertBlocked(facts().promotionRevoked(true).build(),
                RoleRuntimeExecutionBlockReason.PROMOTION_REVOKED);
        assertBlocked(facts().activationHash("0".repeat(64)).build(),
                RoleRuntimeExecutionBlockReason.ACTIVATION_HASH_MISMATCH);
        assertBlocked(facts().promotionHash("0".repeat(64)).build(),
                RoleRuntimeExecutionBlockReason.PROMOTION_HASH_MISMATCH);
        assertBlocked(facts().bindingHash("0".repeat(64)).build(),
                RoleRuntimeExecutionBlockReason.BINDING_HASH_MISMATCH);
        assertBlocked(facts().candidateHash("0".repeat(64)).build(),
                RoleRuntimeExecutionBlockReason.CANDIDATE_HASH_MISMATCH);
        assertBlocked(facts().resolverContractHash("0".repeat(64)).build(),
                RoleRuntimeExecutionBlockReason.RESOLVER_CONTRACT_HASH_MISMATCH);
        assertBlocked(facts().directoryRevision(18).build(),
                RoleRuntimeExecutionBlockReason.DIRECTORY_REVISION_MISMATCH);
        assertBlocked(facts().directoryResultHash("0".repeat(64)).build(),
                RoleRuntimeExecutionBlockReason.DIRECTORY_RESULT_HASH_MISMATCH);
        assertBlocked(facts().businessScope("OTHER").build(),
                RoleRuntimeExecutionBlockReason.BUSINESS_SCOPE_MISMATCH);
        assertBlocked(facts().definitionVersionId("WV-OTHER").build(),
                RoleRuntimeExecutionBlockReason.DEFINITION_VERSION_MISMATCH);
        assertBlocked(facts().nodeId("NODE-OTHER").build(),
                RoleRuntimeExecutionBlockReason.NODE_BINDING_MISMATCH);
    }

    @Test
    void everyExternalCapabilityMustBeReadyAndDegradedMustFailClosed() {
        Fixture fixture = fixture();
        fixture.directory.readiness(RoleRuntimeCapabilityReadiness.NOT_READY);
        assertThat(fixture.admit().reason())
                .isEqualTo(RoleRuntimeExecutionBlockReason.DIRECTORY_CAPABILITY_NOT_READY);

        fixture = fixture();
        fixture.realtime.readiness(RoleRuntimeCapabilityReadiness.NOT_READY);
        assertThat(fixture.admit().reason()).isEqualTo(
                RoleRuntimeExecutionBlockReason.REALTIME_ELIGIBILITY_CAPABILITY_NOT_READY);
        fixture = fixture();
        fixture.dataScope.readiness(RoleRuntimeCapabilityReadiness.DEGRADED);
        assertThat(fixture.admit().reason())
                .isEqualTo(RoleRuntimeExecutionBlockReason.DATA_SCOPE_CAPABILITY_NOT_READY);
        fixture = fixture();
        fixture.sod.readiness(RoleRuntimeCapabilityReadiness.NOT_READY);
        assertThat(fixture.admit().reason())
                .isEqualTo(RoleRuntimeExecutionBlockReason.SOD_CAPABILITY_NOT_READY);
        fixture = fixture();
        fixture.audit.readiness(RoleRuntimeCapabilityReadiness.BLOCKED);
        assertThat(fixture.admit().reason())
                .isEqualTo(RoleRuntimeExecutionBlockReason.AUDIT_CAPABILITY_NOT_READY);
        fixture = fixture();
        fixture.feature.readiness(RoleRuntimeCapabilityReadiness.NOT_READY);
        assertThat(fixture.admit().reason())
                .isEqualTo(RoleRuntimeExecutionBlockReason.FEATURE_FLAG_NOT_READY);
        fixture = fixture();
        fixture.killSwitch.killSwitch(new RoleRuntimeKillSwitch(
                RoleRuntimeKillSwitch.Status.CLOSED, "FAKE_V1"));
        assertThat(fixture.admit().reason())
                .isEqualTo(RoleRuntimeExecutionBlockReason.KILL_SWITCH_NOT_READY);
        fixture = fixture();
        fixture.canary.allowedScope(new RoleRuntimeCanaryScope(
                "ENT-2", "DEF-1", "WV-1", "NODE-A"));
        assertThat(fixture.admit().reason())
                .isEqualTo(RoleRuntimeExecutionBlockReason.CANARY_SCOPE_NOT_ALLOWED);
    }

    @Test
    void resolverMustExistButPreparedMetadataMustRemainNonExecutable() {
        Fixture missing = fixture(new ResolverRegistry(List.of(new ExplicitUserResolver())));
        assertThat(missing.admit().reason())
                .isEqualTo(RoleRuntimeExecutionBlockReason.RESOLVER_DESCRIPTOR_NOT_FOUND);

        AssignmentResolverDescriptor active = new AssignmentResolverDescriptor(
                RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL,
                RoleDirectoryResolver.CONTRACT_HASH, ResolverStatus.ACTIVE, true);
        AssignmentResolver resolver = new AssignmentResolver() {
            @Override public AssignmentResolverDescriptor descriptor() { return active; }
            @Override public CandidatePool resolve(AssignmentResolverContext context) { return null; }
        };
        Fixture illegal = fixture(new ResolverRegistry(List.of(active), List.of(resolver)));
        assertThat(illegal.admit().reason()).isEqualTo(
                RoleRuntimeExecutionBlockReason.RESOLVER_STATUS_NOT_ADMISSION_ELIGIBLE);
    }

    @Test
    void stateMachineMustRejectRecoveryFromRevokedOrExpiredAndHasNoEnabledState() {
        var created = RoleRuntimeExecutionAdmissionDecision.created("ADM-STATE", NOW);
        var validating = created.transition(RoleRuntimeExecutionAdmissionStatus.VALIDATING, NOW);
        var eligible = validating.transition(RoleRuntimeExecutionAdmissionStatus.ELIGIBLE, NOW);
        var revoked = eligible.transition(RoleRuntimeExecutionAdmissionStatus.REVOKED, NOW);
        assertThatThrownBy(() -> revoked.transition(
                RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION, NOW))
                .isInstanceOf(IllegalStateException.class);
        var expired = eligible.transition(RoleRuntimeExecutionAdmissionStatus.EXPIRED, NOW);
        assertThatThrownBy(() -> expired.transition(
                RoleRuntimeExecutionAdmissionStatus.ELIGIBLE, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(Arrays.stream(RoleRuntimeExecutionAdmissionStatus.values())
                .map(Enum::name)).doesNotContain("ENABLED");

        RoleRuntimeExecutionAdmissionDecision approved = fixture().admit();
        RoleRuntimeExecutionAdmissionDecision revokedApproved = approved.transition(
                RoleRuntimeExecutionAdmissionStatus.REVOKED, NOW.plusSeconds(1));
        assertThat(revokedApproved.evidence()).isPresent();
        assertThat(revokedApproved.candidate()).isEmpty();
    }

    @Test
    void canonicalHashMustBeStableUtcAndSensitiveToGovernanceFacts() {
        String first = RoleRuntimeExecutionAdmissionHash.compute(request());
        String second = RoleRuntimeExecutionAdmissionHash.compute(request());
        RoleRuntimeExecutionAdmissionRequest changed = request(
                new RoleRuntimeCanaryScope("ENT-1", "DEF-1", "WV-1", "NODE-B"),
                "NODE-B");
        assertThat(first).isEqualTo(second).matches("[0-9a-f]{64}");
        assertThat(RoleRuntimeExecutionAdmissionHash.compute(changed)).isNotEqualTo(first);
    }

    @Test
    void explicitUserAndLegacyPathsMustRemainOutsideAdmissionAndRegistryMustNotMutate() {
        ResolverRegistry registry = registry();
        List<AssignmentResolverDescriptor> before = registry.descriptors();
        Fixture fixture = fixture(registry);
        fixture.admit();
        assertThat(registry.descriptors()).containsExactlyElementsOf(before);
        assertThat(registry.require(ExplicitUserResolver.CODE).descriptor().status())
                .isEqualTo(ResolverStatus.ACTIVE);
        assertThat(registry.requireDescriptor(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION).status()).isEqualTo(ResolverStatus.PREPARED);
        assertThat(fixture.source.load("LEGACY-TASK-1")).isEmpty();
        assertThat(fixture.source.load("USER-DIRECT-1")).isEmpty();
    }

    private static void assertBlocked(RoleRuntimeExecutionAdmissionFacts value,
            RoleRuntimeExecutionBlockReason reason) {
        Fixture fixture = fixture();
        fixture.source.facts = value;
        RoleRuntimeExecutionAdmissionDecision decision = fixture.admit();
        assertThat(decision.status()).isEqualTo(RoleRuntimeExecutionAdmissionStatus.BLOCKED);
        assertThat(decision.reason()).isEqualTo(reason);
        assertThat(decision.candidate()).isEmpty();
    }

    private static Fixture fixture() { return fixture(registry()); }

    private static Fixture fixture(ResolverRegistry registry) {
        Fixture value = new Fixture();
        value.source = new FakeEvidenceSource(facts().build());
        value.directory = new FakeProductionDirectoryCapabilityAdapter();
        value.realtime = new FakeRealtimeEligibilityCapabilityAdapter();
        value.dataScope = new FakeDataScopeCapabilityAdapter();
        value.sod = new FakeSoDCapabilityAdapter();
        value.audit = new FakeAuditCapabilityAdapter();
        value.feature = new FakeFeatureFlagCapabilityAdapter(scope());
        value.killSwitch = new FakeKillSwitchCapabilityAdapter();
        value.canary = new FakeCanaryScopeAdapter(scope());
        var validator = new RoleRuntimeExecutionAdmissionValidator(registry,
                value.directory, value.realtime, value.dataScope, value.sod, value.audit,
                value.feature, value.killSwitch, value.canary);
        value.service = new RoleRuntimeExecutionAdmissionApplicationService(
                value.source, new RoleRuntimeExecutionAdmissionPolicy(validator));
        return value;
    }

    private static ResolverRegistry registry() {
        ExplicitUserResolver explicit = new ExplicitUserResolver();
        return new ResolverRegistry(List.of(explicit.descriptor(),
                RoleDirectoryResolver.PREPARED_DESCRIPTOR), List.of(explicit));
    }

    private static RoleRuntimeCanaryScope scope() {
        return new RoleRuntimeCanaryScope("ENT-1", "DEF-1", "WV-1", "NODE-A");
    }

    private static RoleRuntimeExecutionAdmissionRequest request() {
        return request(scope(), "NODE-A");
    }

    private static RoleRuntimeExecutionAdmissionRequest request(
            RoleRuntimeCanaryScope canaryScope, String nodeId) {
        return new RoleRuntimeExecutionAdmissionRequest(
                "ACT-1", ACTIVATION, "PROMO-1", PROMOTION, "SNAP-1",
                BINDING, CANDIDATE, RoleDirectoryResolver.CODE.value(),
                RoleDirectoryResolver.VERSION.value(), RoleDirectoryResolver.CONTRACT_HASH.value(),
                17, DIRECTORY, "ENT-1", "INVESTMENT_DECISION", "DEF-1", "WV-1",
                nodeId, EFFECTIVE, canaryScope,
                new RoleRuntimeFeatureFlagPolicy(
                        RoleRuntimeFeatureFlagPolicy.Scope.DEFINITION_VERSION,
                        "FAKE_FLAG_V1", canaryScope), "requester", "ADM-1");
    }

    private static FactsBuilder facts() { return new FactsBuilder(); }

    private static final class Fixture {
        FakeEvidenceSource source;
        FakeProductionDirectoryCapabilityAdapter directory;
        FakeRealtimeEligibilityCapabilityAdapter realtime;
        FakeDataScopeCapabilityAdapter dataScope;
        FakeSoDCapabilityAdapter sod;
        FakeAuditCapabilityAdapter audit;
        FakeFeatureFlagCapabilityAdapter feature;
        FakeKillSwitchCapabilityAdapter killSwitch;
        FakeCanaryScopeAdapter canary;
        RoleRuntimeExecutionAdmissionApplicationService service;
        RoleRuntimeExecutionAdmissionDecision admit() { return service.admit(request(), NOW); }
    }

    private static final class FakeEvidenceSource
            implements cn.gov.enterprise.modules.workflow.domain.role.admission
                    .RoleRuntimeExecutionAdmissionEvidenceSource {
        private RoleRuntimeExecutionAdmissionFacts facts;
        FakeEvidenceSource(RoleRuntimeExecutionAdmissionFacts facts) { this.facts = facts; }
        @Override public Optional<RoleRuntimeExecutionAdmissionFacts> load(String id) {
            return "SNAP-1".equals(id) ? Optional.ofNullable(facts) : Optional.empty();
        }
    }

    private static final class FactsBuilder {
        boolean candidateExists = true;
        RoleRuntimeBindingLifecycleStatus candidateStatus = RoleRuntimeBindingLifecycleStatus.ACTIVE;
        boolean promotionEvidenceComplete = true;
        boolean activationEvidenceComplete = true;
        boolean activationRevoked;
        boolean promotionRevoked;
        String resolverCode = RoleDirectoryResolver.CODE.value();
        String resolverVersion = RoleDirectoryResolver.VERSION.value();
        String resolverContractHash = RoleDirectoryResolver.CONTRACT_HASH.value();
        String activationHash = ACTIVATION;
        String promotionHash = PROMOTION;
        String bindingHash = BINDING;
        String candidateHash = CANDIDATE;
        long directoryRevision = 17;
        String directoryResultHash = DIRECTORY;
        String businessScope = "INVESTMENT_DECISION";
        String definitionId = "DEF-1";
        String definitionVersionId = "WV-1";
        String nodeId = "NODE-A";

        FactsBuilder candidateExists(boolean value) { candidateExists = value; return this; }
        FactsBuilder candidateStatus(RoleRuntimeBindingLifecycleStatus value) { candidateStatus = value; return this; }
        FactsBuilder promotionEvidenceComplete(boolean value) { promotionEvidenceComplete = value; return this; }
        FactsBuilder activationEvidenceComplete(boolean value) { activationEvidenceComplete = value; return this; }
        FactsBuilder activationRevoked(boolean value) { activationRevoked = value; return this; }
        FactsBuilder promotionRevoked(boolean value) { promotionRevoked = value; return this; }
        FactsBuilder resolverContractHash(String value) { resolverContractHash = value; return this; }
        FactsBuilder activationHash(String value) { activationHash = value; return this; }
        FactsBuilder promotionHash(String value) { promotionHash = value; return this; }
        FactsBuilder bindingHash(String value) { bindingHash = value; return this; }
        FactsBuilder candidateHash(String value) { candidateHash = value; return this; }
        FactsBuilder directoryRevision(long value) { directoryRevision = value; return this; }
        FactsBuilder directoryResultHash(String value) { directoryResultHash = value; return this; }
        FactsBuilder businessScope(String value) { businessScope = value; return this; }
        FactsBuilder definitionVersionId(String value) { definitionVersionId = value; return this; }
        FactsBuilder nodeId(String value) { nodeId = value; return this; }

        RoleRuntimeExecutionAdmissionFacts build() {
            return new RoleRuntimeExecutionAdmissionFacts(candidateExists, candidateStatus,
                    promotionEvidenceComplete, activationEvidenceComplete,
                    activationRevoked, promotionRevoked, resolverCode, resolverVersion,
                    resolverContractHash, activationHash, promotionHash, bindingHash,
                    candidateHash, directoryRevision, directoryResultHash, businessScope,
                    definitionId, definitionVersionId, nodeId, EFFECTIVE,
                    EFFECTIVE.plusSeconds(7200));
        }
    }
}
