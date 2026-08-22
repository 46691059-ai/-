package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/** Explicit ROLE_CANDIDATE_CANONICAL_V1 serializer. */
final class RoleCandidateCanonical {
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private RoleCandidateCanonical() { }

    static String hash(RoleCandidateResult result) {
        StringBuilder json = new StringBuilder("{\"candidateUsers\":[");
        var users = result.candidateUsers().stream()
                .sorted(Comparator.comparing(RoleCandidateUser::userId)).toList();
        for (int userIndex = 0; userIndex < users.size(); userIndex++) {
            if (userIndex > 0) json.append(',');
            RoleCandidateUser user = users.get(userIndex);
            json.append("{\"assignments\":[");
            for (int memberIndex = 0; memberIndex < user.assignments().size(); memberIndex++) {
                if (memberIndex > 0) json.append(',');
                RoleDirectoryMember member = user.assignments().get(memberIndex);
                json.append("{\"assignmentId\":").append(quote(member.assignmentId()))
                        .append(",\"effectiveFrom\":").append(quote(time(member.effectiveFrom())))
                        .append(",\"effectiveTo\":")
                        .append(member.effectiveTo() == null ? "null" : quote(time(member.effectiveTo())))
                        .append(",\"revision\":").append(member.revision())
                        .append(",\"sourceRef\":").append(quote(member.sourceRef()))
                        .append(",\"sourceType\":").append(quote(member.sourceType().name()))
                        .append('}');
            }
            json.append("],\"source\":").append(quote(user.source().name()))
                    .append(",\"userId\":").append(quote(user.userId())).append('}');
        }
        json.append("],\"directoryHash\":").append(quote(result.directoryHash()))
                .append(",\"mode\":").append(quote(result.mode().name()))
                .append(",\"organizationId\":").append(quote(result.organizationId()))
                .append(",\"resolverContractHash\":").append(quote(result.resolverContractHash()))
                .append(",\"revision\":").append(result.revision())
                .append(",\"roleCode\":").append(quote(result.roleCode()))
                .append(",\"schema\":\"ROLE_CANDIDATE_CANONICAL_V1\"")
                .append(",\"source\":").append(quote(result.source().name())).append('}');
        return RoleDirectoryCanonical.sha256(json.toString());
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
