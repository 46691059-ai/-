package cn.gov.enterprise.modules.workflow.domain.role.admission;

import java.util.Objects;

public record RoleRuntimeCapabilityResult(
        String capability, RoleRuntimeCapabilityReadiness readiness, String evidence) {

    public RoleRuntimeCapabilityResult {
        capability = RoleRuntimeExecutionAdmissionRequest.text(capability, "capability", 64);
        Objects.requireNonNull(readiness, "readiness");
        evidence = RoleRuntimeExecutionAdmissionRequest.text(evidence, "evidence", 200);
    }

    public boolean ready() {
        return readiness == RoleRuntimeCapabilityReadiness.READY;
    }
}
