package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.util.Objects;

public record PersistentActivationDecision(
        Long id, String activationId, String approverType, String approverId,
        String decision, String reason, String sourceEvidenceHash,
        String decisionHash, String activationHash, String contractHash,
        String bindingHash, Instant decisionTime, int version) {

    public PersistentActivationDecision {
        activationId = PersistentActivationCanonical.text(activationId, "activationId", 100);
        approverType = PersistentActivationCanonical.text(approverType, "approverType", 32);
        approverId = PersistentActivationCanonical.text(approverId, "approverId", 100);
        decision = PersistentActivationCanonical.text(decision, "decision", 16);
        reason = PersistentActivationCanonical.text(reason, "reason", 300);
        sourceEvidenceHash = PersistentActivationCanonical.hash(sourceEvidenceHash, "sourceEvidenceHash");
        decisionHash = PersistentActivationCanonical.hash(decisionHash, "decisionHash");
        activationHash = PersistentActivationCanonical.hash(activationHash, "activationHash");
        contractHash = PersistentActivationCanonical.hash(contractHash, "contractHash");
        bindingHash = PersistentActivationCanonical.hash(bindingHash, "bindingHash");
        Objects.requireNonNull(decisionTime, "decisionTime");
        String expected = PersistentActivationCanonical.decisionHash(activationId, approverType,
                approverId, decision, decisionTime, sourceEvidenceHash);
        if (!expected.equals(decisionHash)) throw new IllegalArgumentException("decision hash mismatch");
    }
}
