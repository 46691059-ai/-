package cn.gov.enterprise.modules.workflow.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleAssignment;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleAssignmentSource;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleAssignmentSourceType;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleAssignmentStatus;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleCanonical;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleCode;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryMember;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class Rc2DirectoryEffectiveAtHashContractTest {
    private static final Instant EFFECTIVE_AT =
            Rc2ControlledCanaryFixtureContract.DIRECTORY_EFFECTIVE_AT;
    private static final Instant TO =
            Rc2ControlledCanaryFixtureContract.ASSIGNMENT_EFFECTIVE_TO;
    private static final Instant RESOLVED_AT = Instant.parse("2026-08-24T06:45:49Z");

    @Test
    void authoritativeEffectiveAtComesFromFixtureManifest() {
        assertThat(Rc2ControlledCanaryFixtureContract.MANIFEST_PATH).isRegularFile();
        assertThat(EFFECTIVE_AT).isBefore(TO);
    }

    @Test
    void assignmentU1IsEffectiveAtContractTime() {
        assertThat(assignment(990303L, 990201L).effectiveAt(EFFECTIVE_AT)).isTrue();
    }

    @Test
    void assignmentU2IsEffectiveAtContractTime() {
        assertThat(assignment(990304L, 990202L).effectiveAt(EFFECTIVE_AT)).isTrue();
    }

    @Test
    void bothAssignmentsProduceExactlyTwoCandidates() {
        assertThat(List.of(assignment(990303L, 990201L), assignment(990304L, 990202L))
                .stream().filter(value -> value.effectiveAt(EFFECTIVE_AT))
                .map(ApprovalRoleAssignment::userId).toList())
                .containsExactly(990201L, 990202L);
    }

    @Test
    void revisionAndDirectoryResultUseSameEffectiveAt() {
        ApprovalRoleDirectoryResult result = result(EFFECTIVE_AT);
        Rc2ControlledCanaryFixtureContract.requireUnified(EFFECTIVE_AT, result.effectiveAt());
    }

    @Test
    void sameCanonicalInputProducesRevisionAndDirectoryHashEquality() {
        ApprovalRoleDirectoryResult revision = result(EFFECTIVE_AT);
        ApprovalRoleDirectoryResult directory = result(EFFECTIVE_AT);
        assertThat(revision.resultHash()).isEqualTo(directory.resultHash())
                .isEqualTo(ApprovalRoleCanonical.resultHash(directory));
    }

    @Test
    void assignmentEvidenceUsesAuthoritativeEffectiveAt() {
        assertThat(assignment(990303L, 990201L).source().recordedAt())
                .isEqualTo(EFFECTIVE_AT);
    }

    @Test
    void mismatchContractFailsClosed() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                Rc2ControlledCanaryFixtureContract.requireUnified(
                        EFFECTIVE_AT, EFFECTIVE_AT.plusSeconds(1)))
                .withMessage("RC2_FIXTURE_EFFECTIVE_AT_MISMATCH");
    }

    @Test
    void executableAssetsContainNoStaleDiscoveryLiteral() throws Exception {
        String stale = "2030" + "-06-01";
        assertThat(executableEffectiveAtText()).doesNotContain(stale);
    }

    @Test
    void manifestIsTheOnlyExecutableAuthoritativeLiteralSource() throws Exception {
        String literal = EFFECTIVE_AT.toString();
        int count = executableEffectiveAtText().split(literal, -1).length - 1;
        assertThat(count).isEqualTo(1);
    }

    @Test
    void preExecutionGateContainsMismatchAndStaleLiteralRejections() throws Exception {
        String collector = Files.readString(fixtureRoot().resolve(
                "collect-rc2-environment-evidence.ps1"));
        String fixtureVerifier = Files.readString(fixtureRoot().resolve(
                "verify-rc2-fixture-contract.ps1"));
        String tests = Files.readString(fixtureRoot().resolve(
                "test-rc2-environment-attestation.ps1"));
        assertThat(collector).contains("fixtureEffectiveAtRaw",
                "Get-RawJsonString $manifestRaw 'directoryEffectiveAt'");
        assertThat(fixtureVerifier).contains("AUTHORITATIVE_EFFECTIVE_AT",
                "ASSIGNMENT_EFFECTIVE_WINDOW");
        assertThat(tests).contains("ISO_STRING_PS51", "ISO_STRING_PS7");
    }

    @Test
    void verifyUsesDerivedContractAndRuntimePolicyRemainsNodeActivatedAt()
            throws Exception {
        String verify = Files.readString(fixtureRoot().resolve("05_rc2_canary_verify.sql"));
        String manifest = Files.readString(fixtureRoot().resolve(
                "rc2-canary-fixture-manifest.json"));
        assertThat(verify).contains("@RC2_CANARY_DIRECTORY_EFFECTIVE_AT",
                "SELECT effective_from", "FROM approval_role_revision");
        assertThat(manifest).contains("NODE_ACTIVATED_AT");
    }

    private static ApprovalRoleAssignment assignment(long id, long userId) {
        ApprovalRoleAssignmentSource source = source("RC2_TEST_CANARY_ASSIGNMENT_" + userId);
        return new ApprovalRoleAssignment(id, "990001", 990101L, 990301L,
                new ApprovalRoleCode("RC1_TEST_CANARY_APPROVER"), userId, EFFECTIVE_AT, TO,
                ApprovalRoleAssignmentStatus.ACTIVE, source,
                ApprovalRoleCanonical.sha256("assignment-" + id), "RC2_TEST_FIXTURE",
                EFFECTIVE_AT, "RC2_TEST_FIXTURE", EFFECTIVE_AT, 0, 0);
    }

    private static ApprovalRoleDirectoryResult result(Instant effectiveAt) {
        List<ApprovalRoleDirectoryMember> members = List.of(
                member(990303L, 990201L), member(990304L, 990202L));
        return ApprovalRoleDirectoryResult.complete("990001", 990101L,
                new ApprovalRoleCode("RC1_TEST_CANARY_APPROVER"), effectiveAt, 1L,
                members, RESOLVED_AT);
    }

    private static ApprovalRoleDirectoryMember member(long assignmentId, long userId) {
        return new ApprovalRoleDirectoryMember(userId, List.of(
                new ApprovalRoleDirectoryMember.Evidence(
                        assignmentId, EFFECTIVE_AT, TO, source("evidence-" + assignmentId))));
    }

    private static ApprovalRoleAssignmentSource source(String reference) {
        return new ApprovalRoleAssignmentSource(
                ApprovalRoleAssignmentSourceType.GOVERNANCE_DECISION,
                "RC2_TEST_FIXTURE", reference, ApprovalRoleCanonical.sha256(reference),
                100, EFFECTIVE_AT);
    }

    private static Path fixtureRoot() {
        return Rc2ControlledCanaryFixtureContract.REPOSITORY_ROOT.resolve(
                "database/test-fixtures/rc2");
    }

    private static String executableEffectiveAtText() throws Exception {
        Path root = Rc2ControlledCanaryFixtureContract.REPOSITORY_ROOT;
        List<Path> paths = List.of(
                fixtureRoot().resolve("rc2-canary-fixture-manifest.json"),
                fixtureRoot().resolve("00_rc2_canary_guard.ps1"),
                fixtureRoot().resolve("04_rc2_canary_publish.ps1"),
                fixtureRoot().resolve("05_rc2_canary_verify.sql"),
                root.resolve("backend/src/test/java/cn/gov/enterprise/modules/workflow/fixture/"
                        + "Rc2ControlledCanaryFixtureContract.java"),
                root.resolve("backend/src/test/java/cn/gov/enterprise/modules/workflow/fixture/"
                        + "Rc2ControlledCanaryFixtureSeederTest.java"),
                root.resolve("backend/src/test/java/cn/gov/enterprise/modules/workflow/fixture/"
                        + "Rc2ControlledCanaryFixtureContractTest.java"),
                root.resolve("backend/src/test/java/cn/gov/enterprise/modules/workflow/fixture/"
                        + "Rc2DirectoryEffectiveAtHashContractTest.java"));
        StringBuilder content = new StringBuilder();
        for (Path path : paths) {
            content.append(Files.readString(path)).append('\n');
        }
        return content.toString();
    }
}
