package cn.gov.enterprise.modules.investment.infrastructure.workflow;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(WorkflowProperties.class)
class WorkflowClientConfig {
    @Bean
    RestClient workflowRestClient(WorkflowProperties properties) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + properties.serviceToken()).build();
    }
}
