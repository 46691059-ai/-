package cn.gov.enterprise.modules.organization.approvalrole.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** ROLE_CANONICAL_JSON_V1-compatible serializer for the Organization boundary. */
public final class ApprovalRoleCanonical {
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private ApprovalRoleCanonical() { }

    public static String resultHash(ApprovalRoleDirectoryResult value) {
        List<FlatEvidence> facts = new ArrayList<>();
        value.members().forEach(member -> member.evidence().forEach(evidence ->
                facts.add(new FlatEvidence(member.userId(), evidence))));
        facts.sort(Comparator.comparingLong(FlatEvidence::userId)
                .thenComparingLong(f -> f.evidence().assignmentId()));
        StringBuilder json = new StringBuilder("{\"complete\":").append(value.complete())
                .append(",\"contractVersion\":").append(quote(ApprovalRoleDirectoryResult.CONTRACT_VERSION))
                .append(",\"effectiveAt\":").append(quote(time(value.effectiveAt())))
                .append(",\"members\":[");
        for (int i = 0; i < facts.size(); i++) {
            if (i > 0) json.append(',');
            FlatEvidence fact = facts.get(i); var evidence = fact.evidence();
            json.append("{\"assignmentId\":").append(quote(Long.toString(evidence.assignmentId())))
                    .append(",\"effectiveFrom\":").append(quote(time(evidence.effectiveFrom())))
                    .append(",\"effectiveTo\":").append(evidence.effectiveTo() == null ? "null" : quote(time(evidence.effectiveTo())))
                    .append(",\"organizationId\":").append(quote(Long.toString(value.organizationId())))
                    .append(",\"revision\":").append(value.revision())
                    .append(",\"roleCode\":").append(quote(value.roleCode().value()))
                    .append(",\"sourceRef\":").append(quote(evidence.source().sourceReference()))
                    .append(",\"sourceType\":").append(quote(evidence.source().sourceType().workflowSourceType()))
                    .append(",\"userId\":").append(quote(Long.toString(fact.userId()))).append('}');
        }
        json.append("],\"organizationId\":").append(quote(Long.toString(value.organizationId())))
                .append(",\"revision\":").append(value.revision())
                .append(",\"roleCode\":").append(quote(value.roleCode().value()))
                .append(",\"schema\":\"ROLE_DIRECTORY_RESULT_HASH_V1\"")
                .append(",\"source\":\"ORGANIZATION_APPROVAL_ROLE_DIRECTORY_V1\"}");
        return sha256(json.toString());
    }

    public static String assignmentKey(String enterpriseId, long organizationId, long roleId,
            long userId, Instant from, Instant to, ApprovalRoleAssignmentSource source) {
        return sha256(String.join("\n", enterpriseId, Long.toString(organizationId),
                Long.toString(roleId), Long.toString(userId), time(from),
                to == null ? "NULL" : time(to), source.sourceSystem(), source.sourceReference()));
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Normalizer.normalize(value, Normalizer.Form.NFC).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static String time(Instant value) { return TIME.format(value); }
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        Normalizer.normalize(value, Normalizer.Form.NFC).codePoints().forEach(c -> {
            switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", c)); else out.appendCodePoint(c); }
            }
        });
        return out.append('"').toString();
    }
    private record FlatEvidence(long userId, ApprovalRoleDirectoryMember.Evidence evidence) { }
}
