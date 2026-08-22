package cn.gov.enterprise.modules.workflow.domain;

import cn.gov.enterprise.modules.workflow.application.service.RoleRealtimeEligibilityPreparationService;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.*;
import cn.gov.enterprise.modules.workflow.support.FakeRealtimeEligibilityCapabilities;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeCapabilityResult.Outcome.*;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRoleRealtimeEligibilityFrameworkTest {
    private static final Instant NOW = Instant.parse("2026-08-20T08:00:00Z");
    private static final String POOL_HASH = hash("pool");
    private static final String BINDING_HASH = hash("binding");
    private static final String CONTRACT_HASH = hash("contract");

    @Test
    void allPassProducesTriStateEvidenceAndAllTwentySevenOrderedChecksWithoutRuntimeMutation() {
        FakeRealtimeEligibilityCapabilities fake = fake();
        RoleRealtimeEligibilityPreparationService service = service();

        var preparation = service.prepare(context(fake, Set.of(99L), true));

        assertThat(preparation.result().decision().status()).isEqualTo(RealtimeEligibilityStatus.ELIGIBLE);
        assertThat(preparation.result().decision().mayEnterClaimVerify()).isTrue();
        assertThat(preparation.result().evidence().validatorResults())
                .extracting(RealtimeEligibilityValidationResult::order).containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 27).boxed().toList());
        assertThat(preparation.verificationToken()).isPresent();
        assertThat(fake.roleDirectoryCalls).hasValue(1);
        // The service has no repository/writer, so ELIGIBLE is governance evidence only.
    }

    @Test
    void nonCandidateIsRejectedBeforeDirectoryAndCannotJoinFrozenPool() {
        FakeRealtimeEligibilityCapabilities fake = fake();
        var result = service().prepare(context(fake, Set.of(100L), true));

        assertThat(result.result().decision().status()).isEqualTo(RealtimeEligibilityStatus.INELIGIBLE);
        assertThat(result.result().decision().failure().code()).isEqualTo(RealtimeEligibilityFailureCode.NOT_CANDIDATE);
        assertThat(fake.roleDirectoryCalls).hasValue(0);
        assertThat(result.verificationToken()).isEmpty();
    }

    @Test
    void userAndRoleFactsUseFailClosedThreeStateSemantics() {
        FakeRealtimeEligibilityCapabilities inactive = fake();
        inactive.user = new RealtimeUserStatusResult(RealtimeUserStatus.DISABLED, "TEST_STATUS_MAP_V1", hash("disabled"));
        assertFailure(inactive, RealtimeEligibilityStatus.INELIGIBLE, RealtimeEligibilityFailureCode.USER_INACTIVE);

        FakeRealtimeEligibilityCapabilities unknown = fake();
        unknown.user = new RealtimeUserStatusResult(RealtimeUserStatus.UNKNOWN, "TEST_STATUS_MAP_V1", hash("unknown"));
        assertFailure(unknown, RealtimeEligibilityStatus.INDETERMINATE, RealtimeEligibilityFailureCode.DIRECTORY_UNAVAILABLE);

        FakeRealtimeEligibilityCapabilities lost = fake();
        lost.role = role(DENY, true, false);
        assertFailure(lost, RealtimeEligibilityStatus.INELIGIBLE, RealtimeEligibilityFailureCode.ROLE_MEMBERSHIP_LOST);

        FakeRealtimeEligibilityCapabilities unavailable = fake();
        unavailable.role = role(INDETERMINATE, false, false);
        assertFailure(unavailable, RealtimeEligibilityStatus.INDETERMINATE, RealtimeEligibilityFailureCode.DIRECTORY_UNAVAILABLE);
    }

    @Test
    void everyNonActiveUserStateIsRejectedAndUnknownRemainsIndeterminate() {
        for (RealtimeUserStatus status : Set.of(RealtimeUserStatus.DISABLED, RealtimeUserStatus.LOCKED,
                RealtimeUserStatus.TERMINATED, RealtimeUserStatus.SUSPENDED)) {
            FakeRealtimeEligibilityCapabilities fake = fake();
            fake.user = new RealtimeUserStatusResult(status, "TEST_STATUS_MAP_V1", hash(status.name()));
            assertFailure(fake, RealtimeEligibilityStatus.INELIGIBLE, RealtimeEligibilityFailureCode.USER_INACTIVE);
        }
    }

    @Test
    void exactOrganizationMembershipLossIsRejected() {
        FakeRealtimeEligibilityCapabilities fake = fake();
        fake.organization = FakeRealtimeEligibilityCapabilities.deny("organization");
        assertFailure(fake, RealtimeEligibilityStatus.INELIGIBLE, RealtimeEligibilityFailureCode.ORG_MEMBERSHIP_LOST);
    }

    @Test
    void candidateRevisionMayAdvanceWhenCurrentEvidenceAndContractRemainValid() {
        var result = service().prepare(context(fake(), Set.of(99L), true)).result();
        assertThat(result.decision().status()).isEqualTo(RealtimeEligibilityStatus.ELIGIBLE);
        assertThat(result.evidence().candidateDirectoryRevision()).isEqualTo("R10");
        assertThat(result.evidence().claimDirectoryRevision()).isEqualTo("R15");
    }

    @Test
    void incompleteAndConflictingDirectoryEvidenceAreIndeterminate() {
        FakeRealtimeEligibilityCapabilities partial = fake();
        partial.role = role(PASS, false, false);
        assertFailure(partial, RealtimeEligibilityStatus.INDETERMINATE, RealtimeEligibilityFailureCode.DIRECTORY_PARTIAL);

        FakeRealtimeEligibilityCapabilities conflict = fake();
        conflict.role = role(PASS, true, true);
        assertFailure(conflict, RealtimeEligibilityStatus.INDETERMINATE, RealtimeEligibilityFailureCode.DIRECTORY_CONFLICT);
    }

    @Test
    void directoryTimeoutOrAdapterExceptionIsFailClosed() {
        FakeRealtimeEligibilityCapabilities fake = fake();
        var base = fake.bundle();
        var throwing = new RealtimeEligibilityCapabilities.Bundle(
                query -> { throw new IllegalStateException("simulated timeout"); },
                base.userStatus(), base.organizationMembership(), base.dataScope(), base.platformSoD(),
                base.businessSoD(), base.audit(), base.featureFlag(), base.killSwitch(), base.canary());
        var result = service().prepare(new RealtimeEligibilityContext(query(), policy(),
                facts(Set.of(99L), true, false), throwing)).result();
        assertThat(result.decision().status()).isEqualTo(RealtimeEligibilityStatus.INDETERMINATE);
        assertThat(result.decision().failure().code()).isEqualTo(RealtimeEligibilityFailureCode.DIRECTORY_UNAVAILABLE);
    }

    @Test
    void dataScopeAndSodKeepBusinessDenySeparateFromCapabilityUnavailable() {
        FakeRealtimeEligibilityCapabilities dataDeny = fake();
        dataDeny.dataScope = FakeRealtimeEligibilityCapabilities.deny("scope");
        assertFailure(dataDeny, RealtimeEligibilityStatus.INELIGIBLE, RealtimeEligibilityFailureCode.DATASCOPE_DENIED);

        FakeRealtimeEligibilityCapabilities dataUnavailable = fake();
        dataUnavailable.dataScope = FakeRealtimeEligibilityCapabilities.indeterminate("scope");
        assertFailure(dataUnavailable, RealtimeEligibilityStatus.INDETERMINATE, RealtimeEligibilityFailureCode.DATASCOPE_UNAVAILABLE);

        FakeRealtimeEligibilityCapabilities platform = fake();
        platform.platformSoD = FakeRealtimeEligibilityCapabilities.deny("platform-sod");
        assertFailure(platform, RealtimeEligibilityStatus.INELIGIBLE, RealtimeEligibilityFailureCode.PLATFORM_SOD_DENIED);

        FakeRealtimeEligibilityCapabilities business = fake();
        business.businessSoD = FakeRealtimeEligibilityCapabilities.deny("business-sod");
        assertFailure(business, RealtimeEligibilityStatus.INELIGIBLE, RealtimeEligibilityFailureCode.BUSINESS_SOD_DENIED);
    }

    @Test
    void featureCanaryKillSwitchAndExistingClaimBlock() {
        FakeRealtimeEligibilityCapabilities kill = fake();
        kill.killSwitch = FakeRealtimeEligibilityCapabilities.deny("kill");
        assertFailure(kill, RealtimeEligibilityStatus.INELIGIBLE, RealtimeEligibilityFailureCode.KILL_SWITCH_ACTIVE);

        FakeRealtimeEligibilityCapabilities feature = fake();
        feature.feature = FakeRealtimeEligibilityCapabilities.deny("feature");
        assertFailure(feature, RealtimeEligibilityStatus.INELIGIBLE, RealtimeEligibilityFailureCode.FEATURE_DISABLED);

        FakeRealtimeEligibilityCapabilities canary = fake();
        canary.canary = FakeRealtimeEligibilityCapabilities.deny("canary");
        assertFailure(canary, RealtimeEligibilityStatus.INELIGIBLE, RealtimeEligibilityFailureCode.CANARY_DENIED);

        FakeRealtimeEligibilityCapabilities fake = fake();
        var context = new RealtimeEligibilityContext(query(), policy(), facts(Set.of(99L), true, true), fake.bundle());
        var result = service().prepare(context).result();
        assertThat(result.decision().failure().code()).isEqualTo(RealtimeEligibilityFailureCode.ACTIVE_CLAIM_EXISTS);
    }

    @Test
    void taskPoolAssigneeAndIdempotencyLocalFactsFailBeforeRuntimeMutation() {
        FakeRealtimeEligibilityCapabilities fake = fake();
        var taskBlocked = new RealtimeEligibilityContext.LocalFacts(true, true, false, true, 2,
                Set.of(99L), true, BINDING_HASH, "APPROVAL_ROLE", "ORG1", "E1", false,
                true, true, 7, "DEF-V1", CONTRACT_HASH, false);
        assertThat(service().prepare(new RealtimeEligibilityContext(query(), policy(), taskBlocked, fake.bundle()))
                .result().decision().failure().code()).isEqualTo(RealtimeEligibilityFailureCode.TASK_NOT_CLAIMABLE);

        var assigneeBlocked = new RealtimeEligibilityContext.LocalFacts(true, true, true, true, 2,
                Set.of(99L), true, BINDING_HASH, "APPROVAL_ROLE", "ORG1", "E1", false,
                false, true, 7, "DEF-V1", CONTRACT_HASH, false);
        assertThat(service().prepare(new RealtimeEligibilityContext(query(), policy(), assigneeBlocked, fake().bundle()))
                .result().decision().failure().code()).isEqualTo(RealtimeEligibilityFailureCode.ASSIGNEE_MISMATCH);

        var idempotencyBlocked = new RealtimeEligibilityContext.LocalFacts(true, true, true, true, 2,
                Set.of(99L), true, BINDING_HASH, "APPROVAL_ROLE", "ORG1", "E1", false,
                true, false, 7, "DEF-V1", CONTRACT_HASH, false);
        assertThat(service().prepare(new RealtimeEligibilityContext(query(), policy(), idempotencyBlocked, fake().bundle()))
                .result().decision().failure().code()).isEqualTo(RealtimeEligibilityFailureCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void verifyRejectsExpiredOrDriftedEvidenceAndNeverCommitsClaim() {
        var preparation = service().prepare(context(fake(), Set.of(99L), true));
        var token = preparation.verificationToken().orElseThrow();
        var service = service();

        assertThat(service.verify(token, new RealtimeEligibilityVerificationFacts(
                POOL_HASH, BINDING_HASH, 7L, false, NOW.plusSeconds(5))).valid()).isTrue();
        assertThat(service.verify(token, new RealtimeEligibilityVerificationFacts(
                POOL_HASH, BINDING_HASH, 7L, false, NOW.plusSeconds(30))).failureCode())
                .isEqualTo(RealtimeEligibilityFailureCode.EVIDENCE_EXPIRED);
        assertThat(service.verify(token, new RealtimeEligibilityVerificationFacts(
                hash("drift"), BINDING_HASH, 7L, false, NOW.plusSeconds(5))).failureCode())
                .isEqualTo(RealtimeEligibilityFailureCode.HASH_MISMATCH);
        assertThat(service.verify(token, new RealtimeEligibilityVerificationFacts(
                POOL_HASH, hash("drift"), 7L, false, NOW.plusSeconds(5))).failureCode())
                .isEqualTo(RealtimeEligibilityFailureCode.BINDING_MISMATCH);
    }

    @Test
    void canonicalHashIsStableAndChangesWithGovernedFacts() {
        var first = service().prepare(context(fake(), Set.of(99L), true)).result().evidence().eligibilityHash();
        var second = service().prepare(context(fake(), Set.of(99L), true)).result().evidence().eligibilityHash();
        assertThat(first).isEqualTo(second);

        RealtimeEligibilityQuery changed = new RealtimeEligibilityQuery("E1", 1, 2, 3, 4, 99,
                "APPROVAL_ROLE", "ORG2", NOW, "R10", hash("candidate-directory"),
                POOL_HASH, BINDING_HASH, "BUSINESS:1", "CORR-1");
        var facts = facts(Set.of(99L), true, false);
        var changedFacts = new RealtimeEligibilityContext.LocalFacts(true, true, true, true, 2,
                Set.of(99L), true, BINDING_HASH, "APPROVAL_ROLE", "ORG2", "E1", false,
                true, true, 7, "DEF-V1", CONTRACT_HASH, false);
        var third = service().prepare(new RealtimeEligibilityContext(changed, policy(), changedFacts, fake().bundle()))
                .result().evidence().eligibilityHash();
        assertThat(third).isNotEqualTo(first);
    }

    @Test
    void canonicalCoversIdentityRevisionCapabilityAndTtlWhileIgnoringValidatorInputOrder() {
        var result = service().prepare(context(fake(), Set.of(99L), true)).result();
        var evidence = result.evidence();
        var reversed = new java.util.ArrayList<>(evidence.validatorResults());
        java.util.Collections.reverse(reversed);
        String baseline = canonical(query(), evidence, evidence.validatorResults(), evidence.expiresAt(),
                evidence.userStatusResult(), evidence.roleMembershipResult(), evidence.dataScopeResult(),
                evidence.platformSoDResult(), evidence.businessSoDResult());
        assertThat(canonical(query(), evidence, reversed, evidence.expiresAt(), evidence.userStatusResult(),
                evidence.roleMembershipResult(), evidence.dataScopeResult(), evidence.platformSoDResult(),
                evidence.businessSoDResult())).isEqualTo(baseline);

        RealtimeEligibilityQuery changedUser = query(100, "APPROVAL_ROLE", "ORG1");
        RealtimeEligibilityQuery changedRole = query(99, "OTHER_ROLE", "ORG1");
        RealtimeEligibilityQuery changedOrg = query(99, "APPROVAL_ROLE", "ORG2");
        assertThat(canonical(changedUser, evidence, reversed, evidence.expiresAt(), evidence.userStatusResult(),
                evidence.roleMembershipResult(), evidence.dataScopeResult(), evidence.platformSoDResult(), evidence.businessSoDResult())).isNotEqualTo(baseline);
        assertThat(canonical(changedRole, evidence, reversed, evidence.expiresAt(), evidence.userStatusResult(),
                evidence.roleMembershipResult(), evidence.dataScopeResult(), evidence.platformSoDResult(), evidence.businessSoDResult())).isNotEqualTo(baseline);
        assertThat(canonical(changedOrg, evidence, reversed, evidence.expiresAt(), evidence.userStatusResult(),
                evidence.roleMembershipResult(), evidence.dataScopeResult(), evidence.platformSoDResult(), evidence.businessSoDResult())).isNotEqualTo(baseline);

        var revisedRole = new RealtimeRoleMembershipResult(PASS, true, false, "R16", hash("directory-result-2"),
                CONTRACT_HASH, "role result", hash("role-2"), NOW.plusSeconds(25));
        var inactiveUser = new RealtimeUserStatusResult(RealtimeUserStatus.DISABLED, "TEST_STATUS_MAP_V1", hash("disabled"));
        var changedScope = FakeRealtimeEligibilityCapabilities.pass("scope-v2", NOW.plusSeconds(25));
        var changedSoD = FakeRealtimeEligibilityCapabilities.pass("sod-v2", NOW.plusSeconds(25));
        assertThat(canonical(query(), evidence, reversed, evidence.expiresAt(), evidence.userStatusResult(), revisedRole,
                evidence.dataScopeResult(), evidence.platformSoDResult(), evidence.businessSoDResult())).isNotEqualTo(baseline);
        assertThat(canonical(query(), evidence, reversed, evidence.expiresAt(), inactiveUser, evidence.roleMembershipResult(),
                evidence.dataScopeResult(), evidence.platformSoDResult(), evidence.businessSoDResult())).isNotEqualTo(baseline);
        assertThat(canonical(query(), evidence, reversed, evidence.expiresAt(), evidence.userStatusResult(), evidence.roleMembershipResult(),
                changedScope, evidence.platformSoDResult(), evidence.businessSoDResult())).isNotEqualTo(baseline);
        assertThat(canonical(query(), evidence, reversed, evidence.expiresAt(), evidence.userStatusResult(), evidence.roleMembershipResult(),
                evidence.dataScopeResult(), changedSoD, evidence.businessSoDResult())).isNotEqualTo(baseline);
        assertThat(canonical(query(), evidence, reversed, evidence.expiresAt().plusSeconds(1), evidence.userStatusResult(),
                evidence.roleMembershipResult(), evidence.dataScopeResult(), evidence.platformSoDResult(), evidence.businessSoDResult())).isNotEqualTo(baseline);
    }

    private static void assertFailure(FakeRealtimeEligibilityCapabilities fake,
                                      RealtimeEligibilityStatus status,
                                      RealtimeEligibilityFailureCode code) {
        var result = service().prepare(context(fake, Set.of(99L), true)).result();
        assertThat(result.decision().status()).isEqualTo(status);
        assertThat(result.decision().failure().code()).isEqualTo(code);
    }

    private static RoleRealtimeEligibilityPreparationService service() {
        return new RoleRealtimeEligibilityPreparationService(new RealtimeEligibilityValidator());
    }

    private static RealtimeEligibilityContext context(FakeRealtimeEligibilityCapabilities fake,
                                                       Set<Long> candidates, boolean memberActive) {
        return new RealtimeEligibilityContext(query(), policy(), facts(candidates, memberActive, false), fake.bundle());
    }

    private static RealtimeEligibilityContext.LocalFacts facts(Set<Long> candidates, boolean memberActive,
                                                                boolean activeClaim) {
        return new RealtimeEligibilityContext.LocalFacts(true, true, true, true, 2, candidates,
                memberActive, BINDING_HASH, "APPROVAL_ROLE", "ORG1", "E1", activeClaim,
                true, true, 7, "DEF-V1", CONTRACT_HASH, false);
    }

    private static RealtimeEligibilityQuery query() {
        return query(99, "APPROVAL_ROLE", "ORG1");
    }

    private static RealtimeEligibilityQuery query(long userId, String roleCode, String organizationId) {
        return new RealtimeEligibilityQuery("E1", 1, 2, 3, 4, userId, roleCode, organizationId, NOW,
                "R10", hash("candidate-directory"), POOL_HASH, BINDING_HASH, "BUSINESS:1", "CORR-1");
    }

    private static String canonical(RealtimeEligibilityQuery query, RealtimeEligibilityEvidence evidence,
                                    java.util.List<RealtimeEligibilityValidationResult> validators,
                                    Instant expiresAt, RealtimeUserStatusResult user,
                                    RealtimeRoleMembershipResult role, RealtimeCapabilityResult dataScope,
                                    RealtimeCapabilityResult platformSoD, RealtimeCapabilityResult businessSoD) {
        return RealtimeEligibilityCanonical.evidenceHash(query, RealtimeEligibilityStatus.ELIGIBLE,
                evidence.verifiedAt(), expiresAt, user, evidence.organizationMembershipResult(), role, dataScope,
                platformSoD, businessSoD, evidence.featureFlagResult(), evidence.canaryResult(),
                evidence.killSwitchResult(), evidence.auditCapabilityResult(), validators);
    }

    private static RealtimeEligibilityPolicy policy() {
        return RealtimeEligibilityPolicy.testPolicy(Duration.ofSeconds(20));
    }

    private static FakeRealtimeEligibilityCapabilities fake() {
        return new FakeRealtimeEligibilityCapabilities(NOW.plusSeconds(25), CONTRACT_HASH);
    }

    private static RealtimeRoleMembershipResult role(RealtimeCapabilityResult.Outcome outcome,
                                                      boolean complete, boolean conflict) {
        return new RealtimeRoleMembershipResult(outcome, complete, conflict, "R15", hash("directory-result"),
                CONTRACT_HASH, "role result", hash("role"), NOW.plusSeconds(25));
    }

    private static String hash(String value) { return FakeRealtimeEligibilityCapabilities.hash(value); }
}
