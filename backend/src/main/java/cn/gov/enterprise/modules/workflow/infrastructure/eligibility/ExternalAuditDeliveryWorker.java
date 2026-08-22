package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="app.workflow-external-audit",name="enabled",havingValue="true")
public class ExternalAuditDeliveryWorker {
    private final ExternalAuditDispatcher dispatcher; private final ExternalAuditDeliveryProperties properties;
    public ExternalAuditDeliveryWorker(ExternalAuditDispatcher dispatcher,ExternalAuditDeliveryProperties properties){this.dispatcher=dispatcher;this.properties=properties;}
    @Scheduled(fixedDelayString="${app.workflow-external-audit.scan-delay-millis:1000}")
    public void dispatch(){dispatcher.dispatch(Math.max(1,Math.min(properties.batchSize(),100)));}
}
