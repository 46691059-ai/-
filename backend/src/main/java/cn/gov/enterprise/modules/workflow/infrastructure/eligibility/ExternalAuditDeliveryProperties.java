package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="app.workflow-external-audit")
public record ExternalAuditDeliveryProperties(boolean enabled,String endpoint,String serviceToken,
        String serviceIdentity,String expectedEnvironment,String expectedProvider,int batchSize,long scanDelayMillis) { }
