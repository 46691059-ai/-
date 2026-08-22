package cn.gov.enterprise.modules.organization.approvalrole.interfaces.internal;

import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryResult;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.approval-role-directory.provider")
public class ApprovalRoleDirectoryProviderProperties {
    public static final String DEFAULT_PROVIDER_CODE = "ORG_GOV_APPROVAL_ROLE_DIRECTORY";
    private boolean enabled;
    private String providerCode = DEFAULT_PROVIDER_CODE;
    private String providerVersion = "1.0.0";
    private String serviceIdentity = "enterprise-platform-approval-role-directory";
    private String environmentIdentity = "TEST";
    private String acceptedClientIdentity = "workflow-role-directory-client";
    private String serviceToken = "";

    public void validateEnabled() {
        if (!enabled) throw new IllegalStateException("approval role directory provider is disabled");
        if (!("TEST".equals(environmentIdentity) || "PREPROD".equals(environmentIdentity))) {
            throw new IllegalStateException("provider environment must be TEST or PREPROD");
        }
        stable(providerCode, "providerCode");
        required(providerVersion, "providerVersion");
        required(serviceIdentity, "serviceIdentity");
        required(acceptedClientIdentity, "acceptedClientIdentity");
        if (serviceToken == null || serviceToken.length() < 32) {
            throw new IllegalStateException("service token must come from a secure environment source and contain at least 32 characters");
        }
    }

    public String contractVersion() { return ApprovalRoleDirectoryResult.CONTRACT_VERSION; }
    public String contractHash() { return ApprovalRoleDirectoryResult.CONTRACT_HASH; }
    public String canonicalVersion() { return ApprovalRoleDirectoryResult.CANONICAL_VERSION; }
    private static void required(String value, String field) { if (value == null || value.isBlank()) throw new IllegalStateException(field + " is required"); }
    private static void stable(String value, String field) { required(value, field); if (!value.matches("[A-Z][A-Z0-9_]{2,99}")) throw new IllegalStateException(field + " must be an uppercase stable code"); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getProviderVersion() { return providerVersion; }
    public void setProviderVersion(String providerVersion) { this.providerVersion = providerVersion; }
    public String getServiceIdentity() { return serviceIdentity; }
    public void setServiceIdentity(String serviceIdentity) { this.serviceIdentity = serviceIdentity; }
    public String getEnvironmentIdentity() { return environmentIdentity; }
    public void setEnvironmentIdentity(String environmentIdentity) { this.environmentIdentity = environmentIdentity; }
    public String getAcceptedClientIdentity() { return acceptedClientIdentity; }
    public void setAcceptedClientIdentity(String acceptedClientIdentity) { this.acceptedClientIdentity = acceptedClientIdentity; }
    public String getServiceToken() { return serviceToken; }
    public void setServiceToken(String serviceToken) { this.serviceToken = serviceToken; }
}
