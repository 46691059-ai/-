package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.workflow.security")
public record WorkflowCallbackSecurityProperties(
        String currentKeyId,
        String currentSecret,
        String previousKeyId,
        String previousSecret,
        long allowedSkewSeconds,
        long nonceTtlSeconds) {

    public WorkflowCallbackSecurityProperties {
        if (allowedSkewSeconds < 1) allowedSkewSeconds = 300;
        if (nonceTtlSeconds < allowedSkewSeconds * 2) nonceTtlSeconds = allowedSkewSeconds * 2;
    }
}
