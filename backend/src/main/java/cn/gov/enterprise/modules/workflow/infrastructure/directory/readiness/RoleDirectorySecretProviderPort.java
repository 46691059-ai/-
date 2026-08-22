package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

/** Capability-only contract. Secret values never cross the readiness model. */
@FunctionalInterface
public interface RoleDirectorySecretProviderPort {
    boolean credentialReferenceAvailable(String providerCode, RoleDirectoryEnvironmentIdentity environment);
}
