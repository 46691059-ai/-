package cn.gov.enterprise.modules.workflow.domain.role.admission;

import java.util.List;
import java.util.Map;

record RoleRuntimeExecutionAdmissionValidation(
        boolean eligible,
        RoleRuntimeExecutionBlockReason reason,
        List<RoleRuntimeExecutionAdmissionCheck> checks,
        Map<String, RoleRuntimeCapabilityResult> capabilities) {

    RoleRuntimeExecutionAdmissionValidation {
        checks = List.copyOf(checks);
        capabilities = Map.copyOf(capabilities);
        if (eligible != (reason == RoleRuntimeExecutionBlockReason.NONE)) {
            throw new IllegalArgumentException("validation result and reason are inconsistent");
        }
    }
}
