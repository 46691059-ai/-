package cn.gov.enterprise.modules.workflow.domain.role.admission;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record RoleRuntimeExecutionAdmissionRequest(
        String activationId, String activationHash,
        String promotionId, String promotionHash,
        String runtimeBindingCandidateId, String bindingHash, String candidateHash,
        String resolverCode, String resolverVersion, String resolverContractHash,
        long directoryRevision, String directoryResultHash,
        String enterpriseId, String businessScope,
        String definitionId, String definitionVersionId, String nodeId,
        Instant effectiveAt, RoleRuntimeCanaryScope canaryScope,
        RoleRuntimeFeatureFlagPolicy featureFlagPolicy,
        String requestedBy, String requestId) {

    private static final Pattern HASH = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern RESOLVER = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    public RoleRuntimeExecutionAdmissionRequest {
        activationId = text(activationId, "activationId", 100);
        activationHash = hash(activationHash, "activationHash");
        promotionId = text(promotionId, "promotionId", 100);
        promotionHash = hash(promotionHash, "promotionHash");
        runtimeBindingCandidateId = text(
                runtimeBindingCandidateId, "runtimeBindingCandidateId", 100);
        bindingHash = hash(bindingHash, "bindingHash");
        candidateHash = hash(candidateHash, "candidateHash");
        resolverCode = resolver(resolverCode, "resolverCode");
        resolverVersion = resolver(resolverVersion, "resolverVersion");
        resolverContractHash = hash(resolverContractHash, "resolverContractHash");
        if (directoryRevision < 0) {
            throw new IllegalArgumentException("directoryRevision must be non-negative");
        }
        directoryResultHash = hash(directoryResultHash, "directoryResultHash");
        enterpriseId = text(enterpriseId, "enterpriseId", 100);
        businessScope = text(businessScope, "businessScope", 200);
        definitionId = text(definitionId, "definitionId", 100);
        definitionVersionId = text(definitionVersionId, "definitionVersionId", 100);
        nodeId = text(nodeId, "nodeId", 100);
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(canaryScope, "canaryScope");
        Objects.requireNonNull(featureFlagPolicy, "featureFlagPolicy");
        requestedBy = text(requestedBy, "requestedBy", 100);
        requestId = text(requestId, "requestId", 100);
        if (!enterpriseId.equals(canaryScope.enterpriseId())
                || !definitionId.equals(canaryScope.definitionId())
                || !definitionVersionId.equals(canaryScope.definitionVersionId())
                || !nodeId.equals(canaryScope.nodeId())) {
            throw new IllegalArgumentException("request and canary scope must describe the same node");
        }
    }

    static String text(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and <= " + max);
        }
        return value;
    }

    static String hash(String value, String field) {
        if (value == null || !HASH.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lower-case SHA-256");
        }
        return value;
    }

    static String resolver(String value, String field) {
        if (value == null || !RESOLVER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be an upper-case resolver identity");
        }
        return value;
    }
}
