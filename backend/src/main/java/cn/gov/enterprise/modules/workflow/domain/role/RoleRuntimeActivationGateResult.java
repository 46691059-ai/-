package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.Objects;

/** Fail-closed Gate result. ELIGIBLE is governance readiness, not Runtime enablement. */
public record RoleRuntimeActivationGateResult(
        RoleRuntimeActivationStatus status, String reason, String activationHash) {

    public RoleRuntimeActivationGateResult {
        Objects.requireNonNull(status, "status");
        if (status != RoleRuntimeActivationStatus.ELIGIBLE
                && status != RoleRuntimeActivationStatus.BLOCKED) {
            throw new IllegalArgumentException("Gate status must be ELIGIBLE or BLOCKED");
        }
        reason = RoleDirectoryQuery.required(reason, "reason", 300);
        activationHash = RoleCandidateResult.hash(activationHash, "activationHash");
    }

    public boolean eligible() {
        return status == RoleRuntimeActivationStatus.ELIGIBLE;
    }
}
