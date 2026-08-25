package cn.gov.enterprise.modules.workflow.domain.role.runtime;

/** Technical execution permission only; it never enables ROLE Runtime or Canary. */
@FunctionalInterface
public interface RoleDirectoryRuntimeTechnicalGate {
    void requireExecutionEligible();

    static RoleDirectoryRuntimeTechnicalGate failClosed() {
        return () -> {
            throw new IllegalStateException(
                    "ROLE_DIRECTORY_TECHNICAL_GATE_BLOCKED");
        };
    }
}
