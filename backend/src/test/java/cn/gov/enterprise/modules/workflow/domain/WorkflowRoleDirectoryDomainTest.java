package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryErrorCode;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryException;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryQuery;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectorySourceType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRoleDirectoryDomainTest {
    private static final Instant EFFECTIVE_AT = Instant.parse("2026-08-13T01:02:03.004Z");

    @Test
    void queryMustEnforceStableRoleAndFrozenContract() {
        RoleDirectoryQuery query = new RoleDirectoryQuery("ENT-001", "ORG-001",
                "INVESTMENT_FINANCE_REVIEWER", EFFECTIVE_AT,
                RoleDirectoryQuery.CONTRACT_VERSION, "trace-1");
        assertThat(query.contractVersion()).isEqualTo("ROLE_DIRECTORY_PORT_V1");

        assertThatThrownBy(() -> new RoleDirectoryQuery("ENT-001", "ORG-001",
                "invalid-role", EFFECTIVE_AT, RoleDirectoryQuery.CONTRACT_VERSION, "trace-1"))
                .isInstanceOf(RoleDirectoryException.class)
                .extracting(error -> ((RoleDirectoryException) error).errorCode())
                .isEqualTo(RoleDirectoryErrorCode.INVALID_DIRECTORY_QUERY);
        assertThatThrownBy(() -> new RoleDirectoryQuery("ENT-001", "ORG-001",
                "VALID_ROLE", EFFECTIVE_AT, "ROLE_DIRECTORY_PORT_V2", "trace-1"))
                .isInstanceOf(RoleDirectoryException.class);
    }

    @Test
    void resultHashMustIgnoreMemberInputOrderAndChangeWithGovernedFacts() {
        RoleDirectoryMember first = member("USER-001", "ARA-001", 7,
                RoleDirectorySourceType.MANUAL_GOVERNANCE);
        RoleDirectoryMember second = member("USER-002", "ARA-002", 3,
                RoleDirectorySourceType.HR_SYNC);
        RoleDirectoryResult ordered = result(12, EFFECTIVE_AT, List.of(first, second));
        RoleDirectoryResult reversed = result(12, EFFECTIVE_AT, List.of(second, first));
        RoleDirectoryResult revisionChanged = result(13, EFFECTIVE_AT, List.of(first, second));
        RoleDirectoryResult timeChanged = result(12,
                Instant.parse("2026-08-14T01:02:03.004Z"), List.of(first, second));
        RoleDirectoryResult memberChanged = result(12, EFFECTIVE_AT, List.of(first));

        assertThat(reversed.resultHash()).isEqualTo(ordered.resultHash());
        assertThat(revisionChanged.resultHash()).isNotEqualTo(ordered.resultHash());
        assertThat(timeChanged.resultHash()).isNotEqualTo(ordered.resultHash());
        assertThat(memberChanged.resultHash()).isNotEqualTo(ordered.resultHash());
        assertThat(ordered.hasValidHash()).isTrue();
    }

    @Test
    void errorCodesAndRetrySemanticsMustRemainCompleteAndFailClosed() {
        assertThat(RoleDirectoryErrorCode.values()).containsExactly(
                RoleDirectoryErrorCode.ROLE_NOT_FOUND,
                RoleDirectoryErrorCode.ROLE_DISABLED,
                RoleDirectoryErrorCode.ROLE_ORG_MISMATCH,
                RoleDirectoryErrorCode.BUSINESS_ORG_INACTIVE,
                RoleDirectoryErrorCode.NO_ROLE_MEMBER,
                RoleDirectoryErrorCode.DIRECTORY_TIMEOUT,
                RoleDirectoryErrorCode.DIRECTORY_UNAVAILABLE,
                RoleDirectoryErrorCode.DIRECTORY_PARTIAL_RESULT,
                RoleDirectoryErrorCode.DIRECTORY_REVISION_MISMATCH,
                RoleDirectoryErrorCode.DIRECTORY_SOURCE_CONFLICT,
                RoleDirectoryErrorCode.CANDIDATE_LIMIT_EXCEEDED,
                RoleDirectoryErrorCode.DIRECTORY_CONTRACT_MISMATCH,
                RoleDirectoryErrorCode.INVALID_DIRECTORY_QUERY);
    }

    static RoleDirectoryMember member(String userId, String assignmentId, long revision,
            RoleDirectorySourceType sourceType) {
        return new RoleDirectoryMember(userId, assignmentId,
                "INVESTMENT_FINANCE_REVIEWER", "ORG-001",
                Instant.parse("2026-01-01T00:00:00Z"), null, sourceType,
                "SOURCE-" + assignmentId, revision);
    }

    static RoleDirectoryResult result(long revision, Instant effectiveAt,
            List<RoleDirectoryMember> members) {
        return RoleDirectoryResult.complete("INVESTMENT_FINANCE_REVIEWER", "ORG-001",
                effectiveAt, revision, members,
                "5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d",
                "FAKE_DIRECTORY");
    }
}
