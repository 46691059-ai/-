package cn.gov.enterprise.modules.workflow.domain.role.runtime;

import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** Canonical evidence embedded in existing Candidate Pool snapshot columns. */
public record RoleDirectoryCandidateEvidence(
        String roleCode, Long organizationId, Instant effectiveAt,
        long directoryRevision, String directoryResultHash,
        String candidateHash, String resolverContractHash, int candidateCount,
        String canonical, String evidenceHash) {
    public static final String VERSION = "ROLE_DIRECTORY_POOL_EVIDENCE_V1";

    public static RoleDirectoryCandidateEvidence freeze(
            RoleDirectoryRuntimeContext context, RoleCandidateResult result) {
        if (!context.roleCode().equals(result.roleCode())
                || !Long.toString(context.resolvedOrganizationId())
                    .equals(result.organizationId())
                || !context.resolverContractHash()
                    .equals(result.resolverContractHash())
                || !result.hasValidCandidateHash()) {
            throw new IllegalArgumentException("ROLE candidate evidence drift");
        }
        String canonical = String.join("\n", VERSION,
                field("roleCode", result.roleCode()),
                field("organizationId", result.organizationId()),
                field("effectiveAt", context.effectiveAt().toString()),
                field("directoryRevision", Long.toString(result.revision())),
                field("directoryResultHash", result.directoryHash()),
                field("candidateHash", result.candidateHash()),
                field("resolverContractHash", result.resolverContractHash()),
                field("candidateCount", Integer.toString(result.candidateUsers().size())));
        return new RoleDirectoryCandidateEvidence(result.roleCode(),
                context.resolvedOrganizationId(), context.effectiveAt(),
                result.revision(), result.directoryHash(), result.candidateHash(),
                result.resolverContractHash(), result.candidateUsers().size(),
                canonical, sha256(canonical));
    }

    public String sourceReference() {
        return "ROLE_DIRECTORY:" + roleCode + ":" + organizationId + ":"
                + directoryRevision;
    }

    private static String field(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return name + "=" + value.length() + ":" + value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
