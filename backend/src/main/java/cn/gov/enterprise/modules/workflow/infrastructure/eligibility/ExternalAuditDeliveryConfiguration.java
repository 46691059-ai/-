package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ExternalAuditDeliveryConfiguration {
    @Bean @ConditionalOnProperty(prefix="app.workflow-external-audit",name="enabled",havingValue="true")
    ExternalAuditDispatcher externalAuditDispatcher(ExternalAuditDeliveryProperties p,JdbcTemplate jdbc,
            WorkflowIdentityGenerator ids,ObjectMapper json,ExternalAuditMetrics metrics){
        if(p.endpoint()==null||p.serviceToken()==null||p.serviceIdentity()==null)throw new IllegalStateException("external audit delivery configuration is incomplete");
        var client=new JdkHttpsExternalAuditSinkClient(HttpClient.newBuilder().build(),json,URI.create(p.endpoint()),
                p.serviceToken(),p.serviceIdentity(),p.expectedEnvironment(),p.expectedProvider());
        client.metadata();
        return new ExternalAuditDispatcher(jdbc,ids,client,json,metrics);
    }
}
