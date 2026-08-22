package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/** Explicit ROLE_BINDING_CANONICAL_V1 serializer. */
final class RoleBindingCanonical {
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private RoleBindingCanonical() { }

    static String hash(ResolverBindingProposalSet set) {
        StringBuilder json = new StringBuilder("{\"definitionId\":")
                .append(set.definitionId()).append(",\"proposals\":[");
        var proposals = set.proposals().stream()
                .sorted(Comparator.comparing(RoleResolverBindingProposal::nodeId)).toList();
        for (int index = 0; index < proposals.size(); index++) {
            if (index > 0) json.append(',');
            RoleResolverBindingProposal proposal = proposals.get(index);
            json.append("{\"candidateMode\":").append(quote(proposal.candidateMode().name()))
                    .append(",\"effectiveAt\":").append(quote(time(proposal.effectiveAt())))
                    .append(",\"nodeId\":").append(proposal.nodeId())
                    .append(",\"organizationId\":").append(quote(proposal.organizationId()))
                    .append(",\"resolverCode\":").append(quote(proposal.resolverCode().value()))
                    .append(",\"resolverContractHash\":").append(quote(proposal.contractHash().value()))
                    .append(",\"resolverVersion\":").append(quote(proposal.resolverVersion().value()))
                    .append(",\"roleCode\":").append(quote(proposal.roleCode())).append('}');
        }
        json.append("],\"schema\":\"ROLE_BINDING_CANONICAL_V1\",\"versionId\":")
                .append(set.versionId()).append('}');
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
