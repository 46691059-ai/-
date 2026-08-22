package cn.gov.enterprise.modules.workflow.domain.role.admission;

public record RoleRuntimeExecutionAdmissionCheck(
        int sequence, String checkCode, boolean passed, RoleRuntimeExecutionBlockReason reason) {

    public RoleRuntimeExecutionAdmissionCheck {
        if (sequence < 1 || sequence > 28) {
            throw new IllegalArgumentException("admission check sequence must be 1..28");
        }
        checkCode = RoleRuntimeExecutionAdmissionRequest.text(checkCode, "checkCode", 100);
        if (reason == null) throw new IllegalArgumentException("reason must not be null");
        if (passed != (reason == RoleRuntimeExecutionBlockReason.NONE)) {
            throw new IllegalArgumentException("check result and reason are inconsistent");
        }
    }
}
