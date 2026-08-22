package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ActivationAuditTrail(
        PersistentActivationRequest request,
        List<PersistentActivationDecision> decisions,
        List<ActivationEvidenceRecord> evidence,
        String auditHash) {
    public ActivationAuditTrail {
        Objects.requireNonNull(request, "request");
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        auditHash = PersistentActivationCanonical.hash(auditHash, "auditHash");
        if (request.status() != PersistentActivationStatus.PERSISTED) {
            throw new IllegalArgumentException("only PERSISTED activation evidence may be stored");
        }
        Set<String> roles = new HashSet<>();
        for (PersistentActivationDecision item : decisions) {
            if (!item.activationId().equals(request.activationId()) || !"APPROVE".equals(item.decision())
                    || !roles.add(item.approverType())) {
                throw new IllegalArgumentException("activation approval evidence is incomplete or duplicated");
            }
        }
        Set<String> required = Set.of("BUSINESS_OWNER", "SECURITY_AUDIT", "RELEASE_APPROVER");
        if (!roles.containsAll(required)) throw new IllegalArgumentException("required approvals are missing");
        EnumSet<ActivationEvidenceType> types = EnumSet.noneOf(ActivationEvidenceType.class);
        evidence.forEach(item -> { if (!item.activationId().equals(request.activationId()) || !types.add(item.evidenceType())) throw new IllegalArgumentException("invalid evidence set"); });
        if (!types.containsAll(EnumSet.allOf(ActivationEvidenceType.class))) {
            throw new IllegalArgumentException("activation evidence is incomplete");
        }
        if (!auditHash.equals(PersistentActivationCanonical.auditHash(request, decisions, evidence))) {
            throw new IllegalArgumentException("activation audit hash mismatch");
        }
    }
}
