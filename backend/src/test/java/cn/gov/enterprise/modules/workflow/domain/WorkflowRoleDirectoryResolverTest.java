package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryErrorCode;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryException;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverContext;
import cn.gov.enterprise.modules.workflow.support.FakeRoleDirectoryAdapter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRoleDirectoryResolverTest {
    private static final Instant EFFECTIVE_AT = Instant.parse("2026-08-13T01:02:03.004Z");

    @Test
    void fakeDirectoryMustSupportSingleAndMultipleMembers() {
        RoleDirectoryResult result = WorkflowRoleDirectoryDomainTest.result(12, EFFECTIVE_AT,
                List.of(WorkflowRoleDirectoryDomainTest.member("USER-002", "ARA-002", 3,
                                cn.gov.enterprise.modules.workflow.domain.role.RoleDirectorySourceType.HR_SYNC),
                        WorkflowRoleDirectoryDomainTest.member("USER-001", "ARA-001", 7,
                                cn.gov.enterprise.modules.workflow.domain.role.RoleDirectorySourceType.MANUAL_GOVERNANCE)));
        var resolved = new RoleDirectoryResolver(FakeRoleDirectoryAdapter.returning(result))
                .resolve(context());

        assertThat(resolved.candidateUsers()).containsExactly("USER-001", "USER-002");
        assertThat(resolved.directoryRevision()).isEqualTo(12);
        assertThat(resolved.resultHash()).isEqualTo(result.resultHash());
    }

    @Test
    void emptyAndPartialResultsMustFailClosed() {
        RoleDirectoryResult empty = WorkflowRoleDirectoryDomainTest.result(12, EFFECTIVE_AT,
                List.of());
        RoleDirectoryResult partial = new RoleDirectoryResult(
                "INVESTMENT_FINANCE_REVIEWER", "ORG-001", EFFECTIVE_AT, 12,
                false, List.of(), "0".repeat(64), RoleDirectoryResolver.CONTRACT_HASH.value(),
                "FAKE_DIRECTORY");

        assertError(FakeRoleDirectoryAdapter.returning(empty), RoleDirectoryErrorCode.NO_ROLE_MEMBER);
        assertError(FakeRoleDirectoryAdapter.returning(partial),
                RoleDirectoryErrorCode.DIRECTORY_PARTIAL_RESULT);
    }

    @Test
    void sourceConflictMustNotBeRetried() {
        FakeRoleDirectoryAdapter fake = new FakeRoleDirectoryAdapter()
                .thenFail(RoleDirectoryErrorCode.DIRECTORY_SOURCE_CONFLICT);
        assertError(fake, RoleDirectoryErrorCode.DIRECTORY_SOURCE_CONFLICT);
        assertThat(fake.calls()).isOne();
    }

    @Test
    void timeoutAndUnavailableMayRetryOnlyOnce() {
        RoleDirectoryResult valid = WorkflowRoleDirectoryDomainTest.result(13, EFFECTIVE_AT,
                List.of(WorkflowRoleDirectoryDomainTest.member("USER-001", "ARA-001", 8,
                        cn.gov.enterprise.modules.workflow.domain.role.RoleDirectorySourceType.MANUAL_GOVERNANCE)));
        FakeRoleDirectoryAdapter recovered = new FakeRoleDirectoryAdapter()
                .thenFail(RoleDirectoryErrorCode.DIRECTORY_TIMEOUT).thenReturn(valid);
        assertThat(new RoleDirectoryResolver(recovered).resolve(context()).directoryRevision())
                .isEqualTo(13);
        assertThat(recovered.calls()).isEqualTo(2);

        FakeRoleDirectoryAdapter unavailable = new FakeRoleDirectoryAdapter()
                .thenFail(RoleDirectoryErrorCode.DIRECTORY_UNAVAILABLE)
                .thenFail(RoleDirectoryErrorCode.DIRECTORY_UNAVAILABLE);
        assertError(unavailable, RoleDirectoryErrorCode.DIRECTORY_UNAVAILABLE);
        assertThat(unavailable.calls()).isEqualTo(2);
    }

    @Test
    void contractAndResultHashDriftMustBeRejected() {
        RoleDirectoryResult valid = WorkflowRoleDirectoryDomainTest.result(12, EFFECTIVE_AT,
                List.of(WorkflowRoleDirectoryDomainTest.member("USER-001", "ARA-001", 7,
                        cn.gov.enterprise.modules.workflow.domain.role.RoleDirectorySourceType.MANUAL_GOVERNANCE)));
        RoleDirectoryResult drifted = new RoleDirectoryResult(valid.roleCode(), valid.organizationId(),
                valid.effectiveAt(), valid.revision(), valid.complete(), valid.members(),
                "0".repeat(64), valid.contractHash(), valid.source());
        assertError(FakeRoleDirectoryAdapter.returning(drifted),
                RoleDirectoryErrorCode.DIRECTORY_CONTRACT_MISMATCH);

        ResolverVersionBinding wrongBinding = new ResolverVersionBinding(1L,
                RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash.of(
                        "0".repeat(64)));
        assertThatThrownBy(() -> new RoleDirectoryResolver(FakeRoleDirectoryAdapter.returning(valid))
                .resolve(new RoleResolverContext(1L, 2L, wrongBinding,
                        "INVESTMENT_FINANCE_REVIEWER", "ORG-001", "ENT-001",
                        EFFECTIVE_AT, "trace-1")))
                .isInstanceOf(RoleDirectoryException.class)
                .extracting(error -> ((RoleDirectoryException) error).errorCode())
                .isEqualTo(RoleDirectoryErrorCode.DIRECTORY_CONTRACT_MISMATCH);
    }

    private static RoleResolverContext context() {
        return new RoleResolverContext(1L, 2L,
                new ResolverVersionBinding(1L, RoleDirectoryResolver.CODE,
                        RoleDirectoryResolver.VERSION, RoleDirectoryResolver.CONTRACT_HASH),
                "INVESTMENT_FINANCE_REVIEWER", "ORG-001", "ENT-001",
                EFFECTIVE_AT, "trace-1");
    }

    private static void assertError(FakeRoleDirectoryAdapter fake, RoleDirectoryErrorCode code) {
        assertThatThrownBy(() -> new RoleDirectoryResolver(fake).resolve(context()))
                .isInstanceOf(RoleDirectoryException.class)
                .extracting(error -> ((RoleDirectoryException) error).errorCode())
                .isEqualTo(code);
    }
}
