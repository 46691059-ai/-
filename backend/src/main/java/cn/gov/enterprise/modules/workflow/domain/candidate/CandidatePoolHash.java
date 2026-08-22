package cn.gov.enterprise.modules.workflow.domain.candidate;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Deterministic, length-prefixed Candidate Pool SHA-256 contract. */
public record CandidatePoolHash(String value) {
    public static final String SCHEMA_VERSION = "CANDIDATE_POOL_HASH_V1";

    public CandidatePoolHash {
        CandidatePoolMember.sha256(value, "candidatePoolHash");
    }

    public static CandidatePoolHash calculate(
            String resolverCode, String resolverVersion, String contractHash,
            String ruleHash, AssignmentStrategy.Type strategyType,
            List<CandidatePoolMember> members) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, SCHEMA_VERSION);
        field(canonical, resolverCode);
        field(canonical, resolverVersion);
        field(canonical, contractHash);
        field(canonical, ruleHash);
        field(canonical, strategyType.name());
        List<CandidatePoolMember> sorted = members.stream()
                .sorted(Comparator.comparingInt(CandidatePoolMember::sortOrder)
                        .thenComparing(CandidatePoolMember::candidateUserId)
                        .thenComparing(member -> member.sourceType().name())
                        .thenComparing(CandidatePoolMember::sourceRefSnapshot))
                .toList();
        field(canonical, Integer.toString(sorted.size()));
        for (CandidatePoolMember member : sorted) {
            field(canonical, Long.toString(member.candidateUserId()));
            field(canonical, member.sourceType().name());
            field(canonical, member.sourceRefSnapshot());
            field(canonical, logical(member.orgIdSnapshot()));
            field(canonical, logical(member.positionIdSnapshot()));
            field(canonical, logical(member.roleIdSnapshot()));
            field(canonical, member.eligibilitySnapshot());
            field(canonical, member.eligibilityHash());
            field(canonical, Integer.toString(member.sortOrder()));
            field(canonical, member.status().name());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return new CandidatePoolHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void field(StringBuilder target, String value) {
        String normalized = CandidatePoolMember.required(value, "hashField", 65535);
        target.append(normalized.length()).append(':').append(normalized).append('|');
    }

    private static String logical(Long value) {
        return value == null ? "-" : Long.toString(value);
    }
}
