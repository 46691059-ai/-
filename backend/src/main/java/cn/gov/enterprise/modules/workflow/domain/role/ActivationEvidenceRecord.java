package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.util.Objects;

public record ActivationEvidenceRecord(
        Long id, Long approvalId, String activationId, ActivationEvidenceType evidenceType,
        String evidenceHash, String activationHash, String contractHash,
        String bindingHash, String canonicalVersion, Instant createdTime) {
    public ActivationEvidenceRecord {
        Objects.requireNonNull(approvalId, "approvalId");
        activationId = PersistentActivationCanonical.text(activationId, "activationId", 100);
        Objects.requireNonNull(evidenceType, "evidenceType");
        evidenceHash = PersistentActivationCanonical.hash(evidenceHash, "evidenceHash");
        activationHash = PersistentActivationCanonical.hash(activationHash, "activationHash");
        contractHash = PersistentActivationCanonical.hash(contractHash, "contractHash");
        bindingHash = PersistentActivationCanonical.hash(bindingHash, "bindingHash");
        canonicalVersion = PersistentActivationCanonical.text(canonicalVersion, "canonicalVersion", 64);
        Objects.requireNonNull(createdTime, "createdTime");
    }
}
