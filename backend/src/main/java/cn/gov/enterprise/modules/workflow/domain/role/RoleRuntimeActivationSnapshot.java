package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable approved governance evidence. Approved explicitly does not mean enabled. */
public record RoleRuntimeActivationSnapshot(
        RoleRuntimeActivationRequest request,
        String activationHash,
        List<RoleRuntimeActivationDecision> decisions,
        Instant approvedAt,
        String evidenceHash) {

    public RoleRuntimeActivationSnapshot {
        Objects.requireNonNull(request, "request");
        activationHash = RoleCandidateResult.hash(activationHash, "activationHash");
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
        Objects.requireNonNull(approvedAt, "approvedAt");
        evidenceHash = RoleCandidateResult.hash(evidenceHash, "evidenceHash");
        if (!evidenceHash.equals(RoleRuntimeActivationCanonical.snapshotHash(
                request, activationHash, decisions))) {
            throw new IllegalArgumentException("activation snapshot evidence hash mismatch");
        }
    }
}
