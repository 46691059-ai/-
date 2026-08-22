package cn.gov.enterprise.modules.governance.audit.interfaces;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="app.governance-audit-sink")
public record ExternalAuditSinkProperties(boolean enabled,String providerCode,String providerVersion,
        String serviceIdentity,String environmentIdentity,String acceptedClientIdentity,String serviceToken) {
    public boolean executableEnvironment(){return "TEST".equals(environmentIdentity)||"PREPROD".equals(environmentIdentity);}
}
