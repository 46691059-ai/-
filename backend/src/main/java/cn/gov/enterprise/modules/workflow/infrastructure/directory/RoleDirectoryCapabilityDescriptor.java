package cn.gov.enterprise.modules.workflow.infrastructure.directory;

/** Read-only governance metadata. READY_FOR_INTEGRATION_TEST is never runtime eligibility. */
public record RoleDirectoryCapabilityDescriptor(
        String providerCode, String providerVersion, String contractVersion, String contractHash,
        AdapterType adapterType, String environmentIdentity, CapabilityStatus capabilityStatus) {
    public enum AdapterType { FAKE, PRODUCTION }
    public enum CapabilityStatus { PREPARED, READY_FOR_INTEGRATION_TEST, BLOCKED }
}
