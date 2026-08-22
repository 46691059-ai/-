package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

/** Security capabilities only; credentials and certificate material are never stored here. */
public record RoleDirectorySecurityReadiness(
        boolean authenticationDefined, boolean leastPrivilegeAuthorization,
        boolean tlsCertificateValidation, boolean hostnameVerification,
        boolean managedSecretSource, boolean logRedaction,
        boolean piiMinimization, boolean accessAudit) {
    public boolean complete() {
        return authenticationDefined && leastPrivilegeAuthorization && tlsCertificateValidation
                && hostnameVerification && managedSecretSource && logRedaction
                && piiMinimization && accessAudit;
    }
}
