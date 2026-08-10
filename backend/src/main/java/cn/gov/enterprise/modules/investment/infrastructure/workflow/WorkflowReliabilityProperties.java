package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.workflow.reliability")
public record WorkflowReliabilityProperties(
        boolean workerEnabled,
        int batchSize,
        int workerThreads,
        int maxRetries,
        long lockSeconds,
        long baseRetrySeconds,
        long maxRetrySeconds,
        long scanDelayMillis) {

    public WorkflowReliabilityProperties {
        if (batchSize < 1) batchSize = 100;
        if (workerThreads < 1) workerThreads = 4;
        if (maxRetries < 1) maxRetries = 8;
        if (lockSeconds < 5) lockSeconds = 60;
        if (baseRetrySeconds < 1) baseRetrySeconds = 30;
        if (maxRetrySeconds < baseRetrySeconds) maxRetrySeconds = 1800;
        if (scanDelayMillis < 100) scanDelayMillis = 1000;
    }
}
