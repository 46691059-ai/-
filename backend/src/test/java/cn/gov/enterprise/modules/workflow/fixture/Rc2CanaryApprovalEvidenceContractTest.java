package cn.gov.enterprise.modules.workflow.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleAssignmentSource;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleAssignmentSourceType;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleCanonical;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleCode;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryMember;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryResult;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBindingCanonical;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryApprovalEvidence;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCanonicalHashSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Rc2CanaryApprovalEvidenceContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path ROOT = Rc2ControlledCanaryFixtureContract.REPOSITORY_ROOT;
    private static final Path FIXTURE = ROOT.resolve("database/test-fixtures/rc2");
    private static final String DIRECTORY_HASH =
            "2e1736fae83be972259ccee92b8d448d463e7c8a5483d49afcda7225a3279234";
    private static final String BINDING_HASH =
            "5b473845390a2a4c69f28a7b9d63d538a0a371cea447e97ecd00707ddbb87c1c";
    private static final String MANIFEST_HASH =
            "e76bc7f8ee3cca977d4363b6073106d66deffe2491d0290a569d46fa3bac57ed";
    private static final String CONTENT_HASH =
            "b2bc64b3c6cebbd1713b92074f5fdcf6862d94fb70338a6b2d07c76c61ef8ade";
    private static final String STRUCTURAL =
            "20253809be2aeb7c76fe36a7d37293b6ca8b77741998aead726893056044a5b9";

    @Test
    void allFourHashesAreReproducibleAndMatchTheReleaseBoundArtifact() throws Exception {
        String directoryFirst = directoryHash();
        String directorySecond = directoryHash();
        String bindingFirst = bindingHash();
        String bindingSecond = bindingHash();
        String manifestFirst = jsonHash("rc2-canary-governance-release-manifest.json");
        String manifestSecond = jsonHash("rc2-canary-governance-release-manifest.json");
        String contentFirst = jsonHash("rc2-canary-approval-content-v1.json");
        String contentSecond = jsonHash("rc2-canary-approval-content-v1.json");

        assertThat(directoryFirst).isEqualTo(directorySecond).isEqualTo(DIRECTORY_HASH);
        assertThat(bindingFirst).isEqualTo(bindingSecond).isEqualTo(BINDING_HASH);
        assertThat(manifestFirst).isEqualTo(manifestSecond).isEqualTo(MANIFEST_HASH);
        assertThat(contentFirst).isEqualTo(contentSecond).isEqualTo(CONTENT_HASH);

        JsonNode evidence = read("rc2-canary-approval-evidence-v1.json");
        JsonNode directoryScope = read("rc2-canary-directory-result-v1.json")
                .path("approvalScope");
        assertThat(directoryScope).isEqualTo(evidence.path("scope"));
        assertThat(evidence.path("directoryResultHash").asText()).isEqualTo(directoryFirst);
        assertThat(evidence.path("versionBindingHash").asText()).isEqualTo(bindingFirst);
        assertThat(evidence.path("manifestHash").asText()).isEqualTo(manifestFirst);
        assertThat(evidence.path("contentHash").asText()).isEqualTo(contentFirst);
        assertThat(evidence.path("structuralFingerprint").asText()).isEqualTo(STRUCTURAL);
        assertThat(evidence.path("hardenedRelease").path("status").asText())
                .isEqualTo("RELEASE_IDENTITY_BOUND");
        assertThat(evidence.path("hardenedRelease").path("tag").asText())
                .isEqualTo("workflow-v1.0.0-rc2.1");
        assertThat(evidence.path("hardenedRelease").path("commit").asText())
                .isEqualTo("c5946d272e8eb88115671d66b46e8c8ec67b1477");
        assertThat(evidence.path("hardenedRelease").path("tagObject").asText())
                .isEqualTo("269595532f17cc3db09880404ca11629d108fc6d");
        assertThat(evidence.path("hardenedRelease").path("tagType").asText())
                .isEqualTo("ANNOTATED");
    }

    @Test
    void finalReleaseBoundArtifactCanInstantiateApprovalEvidenceWithoutGovernanceAction()
            throws Exception {
        JsonNode release = read("rc2-canary-approval-evidence-v1.json").path("hardenedRelease");
        CanaryApprovalEvidence evidence = new CanaryApprovalEvidence("1", 2,
                DIRECTORY_HASH, BINDING_HASH, MANIFEST_HASH, CONTENT_HASH,
                release.path("tag").asText(), release.path("commit").asText(), STRUCTURAL);

        assertThat(evidence.releaseTag()).isEqualTo("workflow-v1.0.0-rc2.1");
        assertThat(evidence.releaseCommit())
                .isEqualTo("c5946d272e8eb88115671d66b46e8c8ec67b1477");
        assertThat(evidence.contentHash()).isEqualTo(CONTENT_HASH);
    }

    @Test
    void postTagAttestationUniquelyBindsReleaseBusinessEvidenceAndScope() throws Exception {
        JsonNode evidence = read("rc2-canary-approval-evidence-v1.json");
        JsonNode attestation = read("rc2-canary-post-tag-release-attestation-v1.json");
        JsonNode hardened = attestation.path("hardenedRelease");

        assertThat(attestation.path("schemaVersion").asText())
                .isEqualTo("RC2_CANARY_POST_TAG_RELEASE_ATTESTATION_V1");
        assertThat(attestation.path("releaseAttestationStatus").asText())
                .isEqualTo("RELEASE_IDENTITY_BOUND");
        assertThat(hardened.path("tag").asText()).isEqualTo("workflow-v1.0.0-rc2.1");
        assertThat(hardened.path("commit").asText())
                .isEqualTo("c5946d272e8eb88115671d66b46e8c8ec67b1477");
        assertThat(hardened.path("tagObject").asText())
                .isEqualTo("269595532f17cc3db09880404ca11629d108fc6d");
        assertThat(hardened.path("tagType").asText()).isEqualTo("ANNOTATED");
        assertThat(hardened.path("remoteBranchHead").asText())
                .isEqualTo(hardened.path("commit").asText());
        assertThat(hardened.path("remoteTagTarget").asText())
                .isEqualTo(hardened.path("commit").asText());
        assertThat(hardened.path("remoteTagObject").asText())
                .isEqualTo(hardened.path("tagObject").asText());
        assertThat(hardened.path("remoteVerified").asBoolean()).isTrue();
        assertThat(attestation.path("businessEvidence").path("directoryResultHash").asText())
                .isEqualTo(evidence.path("directoryResultHash").asText());
        assertThat(attestation.path("businessEvidence").path("versionBindingHash").asText())
                .isEqualTo(evidence.path("versionBindingHash").asText());
        assertThat(attestation.path("businessEvidence").path("manifestHash").asText())
                .isEqualTo(evidence.path("manifestHash").asText());
        assertThat(attestation.path("businessEvidence").path("contentHash").asText())
                .isEqualTo(evidence.path("contentHash").asText());
        assertThat(attestation.path("businessEvidence").path("structuralFingerprint").asText())
                .isEqualTo(evidence.path("structuralFingerprint").asText());
        assertThat(attestation.path("scope")).isEqualTo(evidence.path("scope"));
    }

    @Test
    void contentAndManifestExcludeSelfReferentialReleaseIdentity() throws Exception {
        String manifest = compact("rc2-canary-governance-release-manifest.json");
        String content = compact("rc2-canary-approval-content-v1.json");
        assertThat(manifest).doesNotContain("hardenedRelease", "futureReleaseCommit",
                "releaseTag", "tagObject");
        assertThat(content).doesNotContain("releaseCommit", "releaseTag", "tagObject");
        assertThat(manifest).doesNotContain("C:\\Users\\", "D:\\codex-");
        assertThat(content).doesNotContain("C:\\Users\\", "D:\\codex-");
    }

    private static String directoryHash() throws Exception {
        JsonNode source = read("rc2-canary-directory-result-v1.json");
        Instant effectiveAt = Instant.parse(source.path("effectiveAt").asText());
        List<ApprovalRoleDirectoryMember> members = new ArrayList<>();
        for (JsonNode member : source.path("members")) {
            ApprovalRoleAssignmentSource assignmentSource = new ApprovalRoleAssignmentSource(
                    ApprovalRoleAssignmentSourceType.valueOf(member.path("sourceType").asText()),
                    member.path("sourceSystem").asText(),
                    member.path("sourceReference").asText(),
                    ApprovalRoleCanonical.sha256(member.path("sourceReference").asText()),
                    100, effectiveAt);
            var evidence = new ApprovalRoleDirectoryMember.Evidence(
                    member.path("assignmentId").asLong(),
                    Instant.parse(member.path("effectiveFrom").asText()),
                    Instant.parse(member.path("effectiveTo").asText()), assignmentSource);
            members.add(new ApprovalRoleDirectoryMember(member.path("userId").asLong(),
                    List.of(evidence)));
        }
        ApprovalRoleDirectoryResult result = ApprovalRoleDirectoryResult.complete(
                source.path("enterpriseId").asText(), source.path("organizationId").asLong(),
                new ApprovalRoleCode(source.path("roleCode").asText()), effectiveAt,
                source.path("revision").asLong(), members, Instant.EPOCH);
        return ApprovalRoleCanonical.resultHash(result);
    }

    private static String bindingHash() throws Exception {
        JsonNode source = read("rc2-canary-version-binding-v1.json");
        VersionNodeResolverBinding draft = new VersionNodeResolverBinding(
                source.path("bindingId").asLong(), source.path("definitionId").asLong(),
                source.path("definitionVersionId").asLong(), source.path("nodeId").asLong(),
                source.path("bindingOrder").asInt(),
                ResolverCode.of(source.path("resolverCode").asText()),
                ResolverVersion.of(source.path("resolverVersion").asText()),
                ResolverContractHash.of(source.path("resolverContractHash").asText()),
                AssignmentStrategy.Type.valueOf(source.path("strategyType").asText()),
                ResolverMode.valueOf(source.path("resolverMode").asText()),
                AssignmentStrategy.Type.valueOf(source.path("targetType").asText()),
                source.path("roleCode").asText(),
                OrganizationScopeType.valueOf(source.path("organizationScopeType").asText()),
                source.path("organizationId").asLong(),
                EffectiveTimePolicy.valueOf(source.path("effectiveTimePolicy").asText()),
                source.path("bindingSchemaVersion").asText(), "0".repeat(64), 0);
        return VersionNodeResolverBindingCanonical.compute(draft).bindingHash();
    }

    private static String jsonHash(String file) throws Exception {
        return WorkflowCanonicalHashSupport.sha256(compact(file));
    }

    private static String compact(String file) throws Exception {
        return JSON.writeValueAsString(read(file));
    }

    private static JsonNode read(String file) throws Exception {
        return JSON.readTree(Files.readString(FIXTURE.resolve(file)));
    }
}
