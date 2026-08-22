package cn.gov.enterprise.modules.workflow.domain.role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Canonical persistence envelope; delegates activation semantics to ROLE_RUNTIME_ACTIVATION_CANONICAL_V1. */
public final class PersistentActivationCanonical {
    public static final String VERSION = "ROLE_RUNTIME_ACTIVATION_CANONICAL_V1";

    private PersistentActivationCanonical() { }

    public static String decisionHash(String activationId, String approverType,
            String approverId, String decision, Instant decisionTime, String sourceEvidenceHash) {
        return sha256(String.join("|", VERSION, activationId, approverType, approverId,
                decision, decisionTime.toString(), sourceEvidenceHash));
    }

    public static String auditHash(PersistentActivationRequest request,
            List<PersistentActivationDecision> decisions,
            List<ActivationEvidenceRecord> evidence) {
        String decisionPart = decisions.stream()
                .sorted(Comparator.comparing(PersistentActivationDecision::approverType))
                .map(PersistentActivationDecision::decisionHash).reduce((a, b) -> a + "|" + b).orElse("");
        String evidencePart = evidence.stream()
                .sorted(Comparator.comparing(item -> item.evidenceType().name()))
                .map(item -> item.evidenceType().name() + ':' + item.evidenceHash())
                .reduce((a, b) -> a + "|" + b).orElse("");
        return sha256(String.join("|", VERSION, request.activationId(), request.activationHash(),
                request.resolverCode(), request.resolverVersion(), request.contractHash(),
                request.bindingHash(), request.candidateHash(), request.directoryContractHash(),
                Long.toString(request.directoryRevision()), request.businessScope(),
                request.effectiveAt().toString(), decisionPart, evidencePart));
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static String hash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return value;
    }

    public static String text(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " is required and must not exceed " + max);
        }
        return value;
    }
}
