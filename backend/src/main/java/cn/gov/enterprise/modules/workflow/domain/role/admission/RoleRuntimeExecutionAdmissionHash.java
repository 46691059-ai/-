package cn.gov.enterprise.modules.workflow.domain.role.admission;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RoleRuntimeExecutionAdmissionHash {
    public static final String CANONICAL_VERSION =
            "ROLE_RUNTIME_EXECUTION_ADMISSION_CANONICAL_V1";

    private RoleRuntimeExecutionAdmissionHash() { }

    public static String compute(RoleRuntimeExecutionAdmissionRequest request) {
        StringBuilder canonical = new StringBuilder(CANONICAL_VERSION);
        add(canonical, "activationHash", request.activationHash());
        add(canonical, "promotionHash", request.promotionHash());
        add(canonical, "bindingHash", request.bindingHash());
        add(canonical, "candidateHash", request.candidateHash());
        add(canonical, "resolverCode", request.resolverCode());
        add(canonical, "resolverVersion", request.resolverVersion());
        add(canonical, "resolverContractHash", request.resolverContractHash());
        add(canonical, "directoryRevision", Long.toString(request.directoryRevision()));
        add(canonical, "directoryResultHash", request.directoryResultHash());
        add(canonical, "enterpriseId", request.enterpriseId());
        add(canonical, "businessScope", request.businessScope());
        add(canonical, "definitionId", request.definitionId());
        add(canonical, "definitionVersionId", request.definitionVersionId());
        add(canonical, "nodeId", request.nodeId());
        add(canonical, "effectiveAt", request.effectiveAt().toString());
        add(canonical, "canaryScope", request.canaryScope().canonicalValue());
        add(canonical, "featureFlagPolicy", request.featureFlagPolicy().canonicalValue());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void add(StringBuilder target, String field, String value) {
        target.append('\n').append(field.length()).append(':').append(field)
                .append('=').append(value.length()).append(':').append(value);
    }
}
