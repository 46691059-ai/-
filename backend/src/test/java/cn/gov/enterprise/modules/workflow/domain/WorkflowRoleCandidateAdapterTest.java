package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;
import cn.gov.enterprise.modules.workflow.domain.role.CandidateResolutionMode;
import cn.gov.enterprise.modules.workflow.domain.role.CandidateSource;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateAdapter;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateContext;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateErrorCode;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateException;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectorySourceType;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverContext;
import cn.gov.enterprise.modules.workflow.support.FakeRoleDirectoryAdapter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRoleCandidateAdapterTest {
    private static final Instant EFFECTIVE_AT = Instant.parse("2026-08-13T01:02:03.004Z");
    private static final String ROLE = "INVESTMENT_FINANCE_REVIEWER";
    private static final String ORG = "ORG-001";

    @Test
    void directoryResultMustConvertToSingleOrMultipleCandidateDraft() {
        RoleCandidateResultPair single = resolveAndAdapt(result(ROLE, ORG, 12,
                List.of(member("USER-001", "ARA-001", ROLE, ORG, 7,
                        RoleDirectorySourceType.MANUAL_GOVERNANCE))), 10);
        assertThat(single.result.candidateUsers()).extracting(candidate -> candidate.userId())
                .containsExactly("USER-001");
        assertThat(single.result.source()).isEqualTo(CandidateSource.ROLE_DIRECTORY);
        assertThat(single.result.mode()).isEqualTo(CandidateResolutionMode.ROLE_POOL_PREVIEW);
        assertThat(single.result.hasValidCandidateHash()).isTrue();

        RoleCandidateResultPair multiple = resolveAndAdapt(result(ROLE, ORG, 12,
                List.of(member("USER-002", "ARA-002", ROLE, ORG, 2,
                                RoleDirectorySourceType.HR_SYNC),
                        member("USER-001", "ARA-001", ROLE, ORG, 7,
                                RoleDirectorySourceType.MANUAL_GOVERNANCE))), 10);
        assertThat(multiple.result.candidateUsers()).extracting(candidate -> candidate.userId())
                .containsExactly("USER-001", "USER-002");
    }

    @Test
    void duplicateUsersMustMergeAllAssignmentEvidence() {
        RoleDirectoryResult directory = result(ROLE, ORG, 12, List.of(
                member("USER-001", "ARA-002", ROLE, ORG, 2, RoleDirectorySourceType.HR_SYNC),
                member("USER-001", "ARA-001", ROLE, ORG, 7,
                        RoleDirectorySourceType.MANUAL_GOVERNANCE)));
        var result = new RoleCandidateAdapter(10).adapt(candidateContext(), directory);

        assertThat(result.candidateUsers()).hasSize(1);
        assertThat(result.candidateUsers().getFirst().assignments())
                .extracting(RoleDirectoryMember::assignmentId)
                .containsExactly("ARA-001", "ARA-002");
    }

    @Test
    void memberOrderingMustNotChangeCandidateHash() {
        RoleDirectoryMember first = member("USER-001", "ARA-001", ROLE, ORG, 7,
                RoleDirectorySourceType.MANUAL_GOVERNANCE);
        RoleDirectoryMember second = member("USER-002", "ARA-002", ROLE, ORG, 2,
                RoleDirectorySourceType.HR_SYNC);
        var ordered = new RoleCandidateAdapter(10).adapt(candidateContext(),
                result(ROLE, ORG, 12, List.of(first, second)));
        var reversed = new RoleCandidateAdapter(10).adapt(candidateContext(),
                result(ROLE, ORG, 12, List.of(second, first)));
        assertThat(reversed.candidateHash()).isEqualTo(ordered.candidateHash());
    }

    @Test
    void governedFactChangesMustChangeCandidateHash() {
        RoleDirectoryMember first = member("USER-001", "ARA-001", ROLE, ORG, 7,
                RoleDirectorySourceType.MANUAL_GOVERNANCE);
        RoleCandidateAdapter adapter = new RoleCandidateAdapter(10);
        var base = adapter.adapt(candidateContext(), result(ROLE, ORG, 12, List.of(first)));

        String otherRole = "INVESTMENT_LEGAL_REVIEWER";
        var roleChanged = adapter.adapt(candidateContext(otherRole, ORG),
                result(otherRole, ORG, 12, List.of(member("USER-001", "ARA-001",
                        otherRole, ORG, 7, RoleDirectorySourceType.MANUAL_GOVERNANCE))));
        String otherOrg = "ORG-002";
        var orgChanged = adapter.adapt(candidateContext(ROLE, otherOrg),
                result(ROLE, otherOrg, 12, List.of(member("USER-001", "ARA-001",
                        ROLE, otherOrg, 7, RoleDirectorySourceType.MANUAL_GOVERNANCE))));
        var revisionChanged = adapter.adapt(candidateContext(), result(ROLE, ORG, 13,
                List.of(first)));
        var memberChanged = adapter.adapt(candidateContext(), result(ROLE, ORG, 12,
                List.of(first, member("USER-002", "ARA-002", ROLE, ORG, 3,
                        RoleDirectorySourceType.HR_SYNC))));

        assertThat(List.of(roleChanged.candidateHash(), orgChanged.candidateHash(),
                revisionChanged.candidateHash(), memberChanged.candidateHash()))
                .doesNotContain(base.candidateHash());
    }

    @Test
    void emptyExpiredDuplicateAndLimitCasesMustFailClosed() {
        assertError(() -> new RoleCandidateAdapter(10).adapt(candidateContext(),
                result(ROLE, ORG, 12, List.of())), RoleCandidateErrorCode.EMPTY_ROLE_MEMBER);

        RoleDirectoryMember expired = new RoleDirectoryMember("USER-001", "ARA-001", ROLE, ORG,
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-12-31T00:00:00Z"),
                RoleDirectorySourceType.MANUAL_GOVERNANCE, "SRC-1", 7);
        assertError(() -> new RoleCandidateAdapter(10).adapt(candidateContext(),
                result(ROLE, ORG, 12, List.of(expired))), RoleCandidateErrorCode.EMPTY_ROLE_MEMBER);

        RoleDirectoryMember duplicate = member("USER-001", "ARA-001", ROLE, ORG, 7,
                RoleDirectorySourceType.MANUAL_GOVERNANCE);
        assertError(() -> new RoleCandidateAdapter(10).adapt(candidateContext(),
                result(ROLE, ORG, 12, List.of(duplicate, duplicate))),
                RoleCandidateErrorCode.DUPLICATE_MEMBER);

        assertError(() -> new RoleCandidateAdapter(1).adapt(candidateContext(),
                result(ROLE, ORG, 12, List.of(duplicate,
                        member("USER-002", "ARA-002", ROLE, ORG, 2,
                                RoleDirectorySourceType.HR_SYNC)))),
                RoleCandidateErrorCode.CANDIDATE_LIMIT_EXCEEDED);
    }

    @Test
    void revisionContractAndResultHashDriftMustBeRejected() {
        RoleDirectoryResult valid = result(ROLE, ORG, 12,
                List.of(member("USER-001", "ARA-001", ROLE, ORG, 7,
                        RoleDirectorySourceType.MANUAL_GOVERNANCE)));
        RoleCandidateAdapter adapter = new RoleCandidateAdapter(10);
        assertError(() -> adapter.adapt(candidateContext(), valid, 11),
                RoleCandidateErrorCode.ROLE_DIRECTORY_REVISION_INVALID);

        RoleDirectoryResult hashDrift = new RoleDirectoryResult(valid.roleCode(),
                valid.organizationId(), valid.effectiveAt(), valid.revision(), true,
                valid.members(), "0".repeat(64), valid.contractHash(), valid.source());
        assertError(() -> adapter.adapt(candidateContext(), hashDrift),
                RoleCandidateErrorCode.ROLE_RESULT_HASH_INVALID);

        RoleDirectoryResult contractDrift = new RoleDirectoryResult(valid.roleCode(),
                valid.organizationId(), valid.effectiveAt(), valid.revision(), true,
                valid.members(), valid.resultHash(), "0".repeat(64), valid.source());
        assertError(() -> adapter.adapt(candidateContext(), contractDrift),
                RoleCandidateErrorCode.ROLE_DIRECTORY_CONTRACT_INVALID);
    }

    @Test
    void sourceConflictFromFakeDirectoryMustFailBeforeCandidateAdaptation() {
        FakeRoleDirectoryAdapter fake = new FakeRoleDirectoryAdapter()
                .thenFail(cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryErrorCode.DIRECTORY_SOURCE_CONFLICT);
        assertThatThrownBy(() -> new RoleDirectoryResolver(fake).resolveDirectory(resolverContext()))
                .isInstanceOf(cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryException.class)
                .extracting(error -> ((cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryException) error)
                        .errorCode())
                .isEqualTo(cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryErrorCode.DIRECTORY_SOURCE_CONFLICT);
    }

    private static RoleCandidateResultPair resolveAndAdapt(RoleDirectoryResult result, int limit) {
        RoleDirectoryResult validated = new RoleDirectoryResolver(
                FakeRoleDirectoryAdapter.returning(result)).resolveDirectory(resolverContext());
        return new RoleCandidateResultPair(validated,
                new RoleCandidateAdapter(limit).adapt(candidateContext(), validated));
    }

    private static RoleDirectoryResult result(String role, String org, long revision,
            List<RoleDirectoryMember> members) {
        return RoleDirectoryResult.complete(role, org, EFFECTIVE_AT, revision, members,
                RoleDirectoryResolver.CONTRACT_HASH.value(), "FAKE_DIRECTORY");
    }

    private static RoleDirectoryMember member(String userId, String assignmentId, String role,
            String org, long revision, RoleDirectorySourceType source) {
        return new RoleDirectoryMember(userId, assignmentId, role, org,
                Instant.parse("2026-01-01T00:00:00Z"), null, source,
                "SOURCE-" + assignmentId, revision);
    }

    private static RoleCandidateContext candidateContext() { return candidateContext(ROLE, ORG); }

    private static RoleCandidateContext candidateContext(String role, String org) {
        return new RoleCandidateContext(1L, 2L, role, org, EFFECTIVE_AT,
                new ResolverVersionBinding(1L, RoleDirectoryResolver.CODE,
                        RoleDirectoryResolver.VERSION, RoleDirectoryResolver.CONTRACT_HASH));
    }

    private static RoleResolverContext resolverContext() {
        return new RoleResolverContext(1L, 2L,
                new ResolverVersionBinding(1L, RoleDirectoryResolver.CODE,
                        RoleDirectoryResolver.VERSION, RoleDirectoryResolver.CONTRACT_HASH),
                ROLE, ORG, "ENT-001", EFFECTIVE_AT, "trace-1");
    }

    private static void assertError(ThrowingCall call, RoleCandidateErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(RoleCandidateException.class)
                .extracting(error -> ((RoleCandidateException) error).errorCode())
                .isEqualTo(code);
    }

    private record RoleCandidateResultPair(
            RoleDirectoryResult directory,
            cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateResult result) { }
    @FunctionalInterface private interface ThrowingCall { void run(); }
}
