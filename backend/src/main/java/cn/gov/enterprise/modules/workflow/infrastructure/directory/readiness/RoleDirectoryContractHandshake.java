package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

import java.util.Set;

public record RoleDirectoryContractHandshake(
        String providerCode, String providerVersion, RoleDirectoryEnvironmentIdentity environmentIdentity,
        String contractVersion, String contractHash, String canonicalVersion,
        String canonicalVectorHash, Set<String> supportedCapabilities) {
    public RoleDirectoryContractHandshake {
        providerCode = RoleDirectoryEndpointDescriptor.required(providerCode, "providerCode");
        providerVersion = RoleDirectoryEndpointDescriptor.required(providerVersion, "providerVersion");
        if (environmentIdentity == null) throw new IllegalArgumentException("environmentIdentity is required");
        contractVersion = RoleDirectoryEndpointDescriptor.required(contractVersion, "contractVersion");
        contractHash = RoleDirectoryEndpointDescriptor.hash(contractHash, "contractHash");
        canonicalVersion = RoleDirectoryEndpointDescriptor.required(canonicalVersion, "canonicalVersion");
        canonicalVectorHash = RoleDirectoryEndpointDescriptor.hash(canonicalVectorHash, "canonicalVectorHash");
        supportedCapabilities = Set.copyOf(supportedCapabilities == null ? Set.of() : supportedCapabilities);
    }

    public boolean supports(String capability) { return supportedCapabilities.contains(capability); }
}
