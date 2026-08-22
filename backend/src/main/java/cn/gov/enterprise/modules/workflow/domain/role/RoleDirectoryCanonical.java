package cn.gov.enterprise.modules.workflow.domain.role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Explicit ROLE_CANONICAL_JSON_V1 serializer; independent from JSON library defaults. */
final class RoleDirectoryCanonical {
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private RoleDirectoryCanonical() { }

    static String resultHash(RoleDirectoryResult value) {
        List<RoleDirectoryMember> members = value.members().stream()
                .sorted(Comparator.comparing(RoleDirectoryMember::userId)
                        .thenComparing(RoleDirectoryMember::assignmentId))
                .toList();
        StringBuilder json = new StringBuilder("{\"complete\":").append(value.complete())
                .append(",\"contractVersion\":").append(quote(RoleDirectoryQuery.CONTRACT_VERSION))
                .append(",\"effectiveAt\":").append(quote(time(value.effectiveAt())))
                .append(",\"members\":[");
        for (int index = 0; index < members.size(); index++) {
            if (index > 0) json.append(',');
            RoleDirectoryMember member = members.get(index);
            json.append("{\"assignmentId\":").append(quote(member.assignmentId()))
                    .append(",\"effectiveFrom\":").append(quote(time(member.effectiveFrom())))
                    .append(",\"effectiveTo\":")
                    .append(member.effectiveTo() == null ? "null" : quote(time(member.effectiveTo())))
                    .append(",\"organizationId\":").append(quote(member.organizationId()))
                    .append(",\"revision\":").append(member.revision())
                    .append(",\"roleCode\":").append(quote(member.roleCode()))
                    .append(",\"sourceRef\":").append(quote(member.sourceRef()))
                    .append(",\"sourceType\":").append(quote(member.sourceType().name()))
                    .append(",\"userId\":").append(quote(member.userId())).append('}');
        }
        json.append("],\"organizationId\":").append(quote(value.organizationId()))
                .append(",\"revision\":").append(value.revision())
                .append(",\"roleCode\":").append(quote(value.roleCode()))
                .append(",\"schema\":\"ROLE_DIRECTORY_RESULT_HASH_V1\"")
                .append(",\"source\":").append(quote(value.source())).append('}');
        return sha256(json.toString());
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String time(Instant value) { return TIME.format(value); }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (codePoint < 0x20) result.append(String.format("\\u%04x", codePoint));
                    else result.appendCodePoint(codePoint);
                }
            }
        });
        return result.append('"').toString();
    }
}
