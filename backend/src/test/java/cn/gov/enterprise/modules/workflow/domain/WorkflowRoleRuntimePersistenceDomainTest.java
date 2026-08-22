package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeBindingSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeApproval;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeBindingSnapshot;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimePersistencePolicy;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimePersistenceDomainTest {
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);
    private static final String HASH_D = "d".repeat(64);
    private static final String HASH_E = "e".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-13T02:03:04.005Z");

    @Test
    void approvalStateMachineMustFailClosed() {
        RoleRuntimeApproval pending = pending();
        RoleRuntimeApproval approved = pending.approve("governance-admin", NOW);
        RoleRuntimeApproval rejected = pending.reject("contract evidence mismatch");

        assertThat(approved.status()).isEqualTo(RoleRuntimeApproval.Status.APPROVED);
        assertThat(approved.approved()).isTrue();
        assertThat(rejected.status()).isEqualTo(RoleRuntimeApproval.Status.REJECTED);
        assertThat(approved.expire().status()).isEqualTo(RoleRuntimeApproval.Status.EXPIRED);
        assertThatThrownBy(() -> approved.approve("other", NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(rejected::expire).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bindingSnapshotHashMustBeStableAndCoverEveryGovernedField() {
        RoleRuntimeEvidence evidence = evidence(12, HASH_B, HASH_C, HASH_D, HASH_E);
        String stable = hash("ROLE_DIRECTORY_V1", "1", HASH_A,
                "INVESTMENT_REVIEWER", "ORG-001", evidence, NOW);
        assertThat(hash("ROLE_DIRECTORY_V1", "1", HASH_A,
                "INVESTMENT_REVIEWER", "ORG-001", evidence, NOW)).isEqualTo(stable);

        assertThat(hash("ROLE_DIRECTORY_V2", "1", HASH_A,
                "INVESTMENT_REVIEWER", "ORG-001", evidence, NOW)).isNotEqualTo(stable);
        assertThat(hash("ROLE_DIRECTORY_V1", "2", HASH_A,
                "INVESTMENT_REVIEWER", "ORG-001", evidence, NOW)).isNotEqualTo(stable);
        assertThat(hash("ROLE_DIRECTORY_V1", "1", "0".repeat(64),
                "INVESTMENT_REVIEWER", "ORG-001", evidence, NOW)).isNotEqualTo(stable);
        assertThat(hash("ROLE_DIRECTORY_V1", "1", HASH_A,
                "BOARD_REVIEWER", "ORG-001", evidence, NOW)).isNotEqualTo(stable);
        assertThat(hash("ROLE_DIRECTORY_V1", "1", HASH_A,
                "INVESTMENT_REVIEWER", "ORG-002", evidence, NOW)).isNotEqualTo(stable);
        assertThat(hash("ROLE_DIRECTORY_V1", "1", HASH_A,
                "INVESTMENT_REVIEWER", "ORG-001",
                evidence(13, HASH_B, HASH_C, HASH_D, HASH_E), NOW)).isNotEqualTo(stable);
        assertThat(hash("ROLE_DIRECTORY_V1", "1", HASH_A,
                "INVESTMENT_REVIEWER", "ORG-001", evidence, NOW.plusSeconds(1)))
                .isNotEqualTo(stable);
    }

    @Test
    void onlyApprovedEvidenceMayFreezeAnImmutableSnapshot() {
        assertThatThrownBy(() -> freeze(pending()))
                .isInstanceOf(IllegalStateException.class);

        RoleRuntimeBindingSnapshot snapshot = freeze(pending().approve("approver", NOW));
        assertThat(snapshot.status()).isEqualTo(RoleRuntimeBindingSnapshot.Status.FROZEN);
        assertThat(snapshot.bindingHash()).isEqualTo(hash("ROLE_DIRECTORY_V1", "1",
                HASH_A, "INVESTMENT_REVIEWER", "ORG-001",
                evidence(12, HASH_B, HASH_C, HASH_D, HASH_E), NOW));
        assertThat(Arrays.stream(RoleRuntimeBindingSnapshotRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain("update", "save", "delete");
    }

    @Test
    void illegalOrDriftedHashMustBeRejectedBeforePersistence() {
        assertThatThrownBy(() -> new RoleRuntimeBindingSnapshot(
                20L, 10L, 30L, 31L, 32L, 40L, 50L, 60L,
                ResolverCode.of("ROLE_DIRECTORY_V1"), ResolverVersion.of("1"),
                ResolverContractHash.of(HASH_A), "INVESTMENT_REVIEWER", "ORG-001",
                evidence(12, HASH_B, HASH_C, HASH_D, HASH_E), NOW,
                "0".repeat(64), RoleRuntimeBindingSnapshot.Status.FROZEN,
                "test evidence", 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bindingHash mismatch");
    }

    private static RoleRuntimeApproval pending() {
        return RoleRuntimeApproval.pending(10L, HASH_B, HASH_C,
                ResolverCode.of("ROLE_DIRECTORY_V1"), ResolverVersion.of("1"),
                ResolverContractHash.of(HASH_A), "test governance evidence");
    }

    private static RoleRuntimeBindingSnapshot freeze(RoleRuntimeApproval approval) {
        return RoleRuntimePersistencePolicy.freeze(20L, approval, 30L, 31L, 32L,
                40L, 50L, 60L, "INVESTMENT_REVIEWER", "ORG-001",
                evidence(12, HASH_B, HASH_C, HASH_D, HASH_E), NOW,
                "test runtime evidence");
    }

    private static RoleRuntimeEvidence evidence(
            long revision, String directory, String roleRule,
            String candidateRule, String source) {
        return new RoleRuntimeEvidence(revision, directory, roleRule, candidateRule, source);
    }

    private static String hash(
            String resolverCode, String resolverVersion, String contractHash,
            String roleCode, String organizationId, RoleRuntimeEvidence evidence,
            Instant effectiveAt) {
        return RoleRuntimePersistencePolicy.hash(ResolverCode.of(resolverCode),
                ResolverVersion.of(resolverVersion), ResolverContractHash.of(contractHash),
                roleCode, organizationId, evidence, effectiveAt);
    }
}
