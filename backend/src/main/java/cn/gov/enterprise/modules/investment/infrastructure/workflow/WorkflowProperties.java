package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.workflow")
public record WorkflowProperties(
        String baseUrl, String serviceToken, String callbackToken,
        String decisionDefinitionKey, int decisionDefinitionVersion) {
    public WorkflowProperties {
        if (decisionDefinitionKey == null || decisionDefinitionKey.isBlank()) {
            decisionDefinitionKey = "investment-decision";
        }
        if (decisionDefinitionVersion < 1) decisionDefinitionVersion = 1;
    }
}
