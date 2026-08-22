package cn.gov.enterprise.modules.organization.approvalrole.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record ApprovalRoleDirectoryResult(String enterpriseId, long organizationId,
        ApprovalRoleCode roleCode, Instant effectiveAt, long revision, boolean complete,
        List<ApprovalRoleDirectoryMember> members, String resultHash, String contractVersion,
        String contractHash, String canonicalVersion, Instant resolvedAt) {
    public static final String CONTRACT_VERSION = "ROLE_DIRECTORY_PORT_V1";
    public static final String CONTRACT_HASH = "5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d";
    public static final String CANONICAL_VERSION = "ROLE_CANONICAL_JSON_V1";

    public ApprovalRoleDirectoryResult {
        enterpriseId = ApprovalRole.required(enterpriseId, "enterpriseId", 100);
        if (organizationId <= 0 || revision <= 0) throw new IllegalArgumentException("invalid directory identity");
        if (roleCode == null || effectiveAt == null || resolvedAt == null) throw new IllegalArgumentException("directory facts must not be null");
        members = List.copyOf(members == null ? List.of() : members).stream()
                .sorted(Comparator.comparingLong(ApprovalRoleDirectoryMember::userId)).toList();
        ApprovalRoleAssignment.hash(resultHash, "resultHash");
        if (!CONTRACT_VERSION.equals(contractVersion) || !CONTRACT_HASH.equals(contractHash)
                || !CANONICAL_VERSION.equals(canonicalVersion)) throw new IllegalArgumentException("frozen directory contract mismatch");
    }

    public static ApprovalRoleDirectoryResult complete(String enterpriseId, long organizationId,
            ApprovalRoleCode roleCode, Instant effectiveAt, long revision,
            List<ApprovalRoleDirectoryMember> members, Instant resolvedAt) {
        ApprovalRoleDirectoryResult draft = new ApprovalRoleDirectoryResult(enterpriseId, organizationId,
                roleCode, effectiveAt, revision, true, members, "0".repeat(64),
                CONTRACT_VERSION, CONTRACT_HASH, CANONICAL_VERSION, resolvedAt);
        return new ApprovalRoleDirectoryResult(enterpriseId, organizationId, roleCode, effectiveAt,
                revision, true, members, ApprovalRoleCanonical.resultHash(draft), CONTRACT_VERSION,
                CONTRACT_HASH, CANONICAL_VERSION, resolvedAt);
    }
}
