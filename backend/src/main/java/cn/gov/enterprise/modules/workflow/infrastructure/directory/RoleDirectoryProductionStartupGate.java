package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import java.util.List;
import java.util.Objects;

/**
 * Pure startup contract evaluator. Passing this gate never mutates ResolverRegistry and never
 * changes ROLE_DIRECTORY_V1 from PREPARED/non-executable.
 */
public final class RoleDirectoryProductionStartupGate {
    private final AdapterQualificationPolicy qualificationPolicy = new AdapterQualificationPolicy();

    public GateResult evaluate(Input input) {
        try {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(input.properties(), "properties");
            if (input.clientCount() != 1) throw blocked("exactly one transport client is required");
            qualificationPolicy.verify(input.properties(), input.adapters());
            if (!input.productionDependencyPolicyPassed()) {
                throw blocked("production dependency policy failed");
            }
            AssignmentResolverDescriptor resolver = Objects.requireNonNull(input.roleResolver(), "roleResolver");
            if (!resolver.code().equals(RoleDirectoryResolver.CODE)
                    || !resolver.version().equals(RoleDirectoryResolver.VERSION)
                    || !resolver.contractHash().equals(RoleDirectoryResolver.CONTRACT_HASH)
                    || resolver.status() != ResolverStatus.PREPARED || resolver.enabled()) {
                throw blocked("ROLE_DIRECTORY_V1 must remain PREPARED and non-executable");
            }
            return new GateResult(RoleDirectoryCapabilityDescriptor.CapabilityStatus.READY_FOR_INTEGRATION_TEST,
                    List.of(), false);
        } catch (RuntimeException exception) {
            return new GateResult(RoleDirectoryCapabilityDescriptor.CapabilityStatus.BLOCKED,
                    List.of(exception.getMessage()), false);
        }
    }

    private DirectoryFailure blocked(String message) {
        return new DirectoryFailure(DirectoryFailure.Code.INVALID_CONFIGURATION, message);
    }

    public record Input(RoleDirectoryClientProperties properties,
            List<RoleDirectoryCapabilityDescriptor> adapters, int clientCount,
            boolean productionDependencyPolicyPassed, AssignmentResolverDescriptor roleResolver) {
        public Input { adapters = List.copyOf(adapters == null ? List.of() : adapters); }
    }

    public record GateResult(RoleDirectoryCapabilityDescriptor.CapabilityStatus status,
            List<String> reasons, boolean resolverExecutionEligible) {
        public GateResult { reasons = List.copyOf(reasons); }
    }
}
