package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({WorkflowReliabilityProperties.class, WorkflowCallbackSecurityProperties.class})
class WorkflowReliabilityConfig {
    @Bean("workflowOutboxExecutor")
    Executor workflowOutboxExecutor(WorkflowReliabilityProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerThreads());
        executor.setMaxPoolSize(properties.workerThreads());
        executor.setQueueCapacity(properties.batchSize() * 2);
        executor.setThreadNamePrefix("workflow-outbox-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
