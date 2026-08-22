package cn.gov.enterprise.modules.workflow.domain.service;

import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Stable hashes for resolver rule, node binding and the complete instance manifest. */
public final class WorkflowResolverBindingHasher {
    public static final String MANIFEST_VERSION = "MULTI_RESOLVER_V1";
    public static final String RULE_VERSION = "ASSIGNMENT_RULE_V1";

    public String ruleHash(String strategyType, String mode, String targetType,
                           String targetValue, String ruleSnapshot) {
        return digest(canonical(strategyType, mode, targetType, targetValue,
                RULE_VERSION, ruleSnapshot));
    }

    public String nodeHash(String nodeCode, WorkflowResolverBinding resolver,
                           String targetType, String targetValue, String ruleHash) {
        return digest(canonical(MANIFEST_VERSION, nodeCode, resolver.resolverCode().value(),
                resolver.resolverVersion().value(), resolver.contractHash().value(),
                resolver.strategyType().name(), resolver.resolverMode().name(), targetType,
                targetValue, RULE_VERSION, ruleHash));
    }

    public String manifestHash(List<NodeResolverBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            throw new IllegalArgumentException("node bindings must not be empty");
        }
        String canonical = bindings.stream()
                .sorted(Comparator.comparing(NodeResolverBinding::nodeCodeSnapshot))
                .map(NodeResolverBinding::nodeBindingHash)
                .reduce("", (left, right) -> left + canonical(right));
        return digest(canonical);
    }

    private String canonical(Object... values) {
        StringBuilder value = new StringBuilder();
        for (Object item : values) {
            String text = Objects.toString(item, "");
            value.append(text.length()).append(':').append(text).append('|');
        }
        return value.toString();
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
