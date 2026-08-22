package cn.gov.enterprise.modules.organization.approvalrole.interfaces.internal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class ApprovalRoleDirectoryProviderDtos {
    private ApprovalRoleDirectoryProviderDtos() { }

    public record Health(String status, boolean applicationAlive, boolean databaseConnected,
            boolean directorySchemaAvailable, Instant checkedAt) { }
    public record Metadata(String providerCode, String providerVersion, String serviceIdentity,
            String environmentIdentity, String contractVersion, String contractHash,
            String canonicalVersion, String canonicalVectorHash, Set<String> supportedCapabilities,
            String revisionSemantics, boolean historicalQuerySupported,
            String completeSemantics, boolean paginationSupported) { }
    public record CanonicalVerifyRequest(String expectedHash) { }
    public record CanonicalVerifyResult(String canonicalVersion, String vectorHash, boolean matches) { }
    public record ResolveRequest(String enterpriseId, String organizationId, String roleCode,
            Instant effectiveAt, String correlationId) { }
    public record Member(String userId, String assignmentId, String roleCode, String organizationId,
            Instant effectiveFrom, Instant effectiveTo, String sourceType, String sourceRef, long revision) { }
    public record ResolveResult(String enterpriseId, String organizationId, String roleCode,
            Instant effectiveAt, long revision, boolean complete, List<Member> members,
            String resultHash, String contractVersion, String contractHash,
            String canonicalVersion, String providerCode, String providerVersion,
            String environmentIdentity, Instant resolvedAt, String source) {
        public ResolveResult { members = List.copyOf(members == null ? List.of() : members); }
    }
}
