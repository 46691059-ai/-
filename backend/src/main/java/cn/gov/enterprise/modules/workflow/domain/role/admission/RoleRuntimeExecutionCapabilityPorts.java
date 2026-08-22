package cn.gov.enterprise.modules.workflow.domain.role.admission;

/** Port group for production-readiness checks. Implementations must be side-effect free. */
public final class RoleRuntimeExecutionCapabilityPorts {
    private RoleRuntimeExecutionCapabilityPorts() { }

    @FunctionalInterface
    public interface DirectoryCapabilityPort {
        RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request);
    }

    @FunctionalInterface
    public interface RealtimeEligibilityCapabilityPort {
        RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request);
    }

    @FunctionalInterface
    public interface DataScopeCapabilityPort {
        RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request);
    }

    @FunctionalInterface
    public interface SoDCapabilityPort {
        RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request);
    }

    @FunctionalInterface
    public interface AuditCapabilityPort {
        RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request);
    }

    @FunctionalInterface
    public interface FeatureFlagCapabilityPort {
        RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request);
    }

    @FunctionalInterface
    public interface KillSwitchCapabilityPort {
        RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request);
    }

    @FunctionalInterface
    public interface CanaryScopePort {
        RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request);
    }
}
