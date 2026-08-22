package cn.gov.enterprise.modules.workflow.domain.role.admission;

import java.util.Objects;

public record RoleRuntimeKillSwitch(Status status, String policyVersion) {
    public enum Status { OPEN, CLOSED }

    public RoleRuntimeKillSwitch {
        Objects.requireNonNull(status, "status");
        policyVersion = RoleRuntimeExecutionAdmissionRequest.text(
                policyVersion, "policyVersion", 64);
    }

    public boolean allowsNewAdmission() {
        return status == Status.OPEN;
    }
}
