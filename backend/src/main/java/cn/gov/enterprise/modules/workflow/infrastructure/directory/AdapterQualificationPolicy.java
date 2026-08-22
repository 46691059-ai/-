package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import java.util.List;

/** Qualification is explicit and never based on @Primary or bean ordering. */
public final class AdapterQualificationPolicy {
    public void verify(RoleDirectoryClientProperties properties,
            List<RoleDirectoryCapabilityDescriptor> descriptors) {
        long productionCount = descriptors.stream()
                .filter(item -> item.adapterType() == RoleDirectoryCapabilityDescriptor.AdapterType.PRODUCTION)
                .count();
        if (productionCount != 1) throw blocked("exactly one production adapter is required");
        if (properties.production() && descriptors.stream().anyMatch(item ->
                item.adapterType() == RoleDirectoryCapabilityDescriptor.AdapterType.FAKE)) {
            throw blocked("Fake directory adapter is forbidden in production");
        }
        RoleDirectoryCapabilityDescriptor production = descriptors.stream()
                .filter(item -> item.adapterType() == RoleDirectoryCapabilityDescriptor.AdapterType.PRODUCTION)
                .findFirst().orElseThrow();
        if (!properties.providerCode().equals(production.providerCode())
                || !properties.expectedContractVersion().equals(production.contractVersion())
                || !properties.expectedContractHash().equals(production.contractHash())
                || !properties.environmentIdentity().equals(production.environmentIdentity())) {
            throw blocked("production adapter metadata does not match typed configuration");
        }
    }

    private DirectoryFailure blocked(String message) {
        return new DirectoryFailure(DirectoryFailure.Code.INVALID_CONFIGURATION, message);
    }
}
