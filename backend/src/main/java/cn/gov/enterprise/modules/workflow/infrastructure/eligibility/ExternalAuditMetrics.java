package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import io.micrometer.core.instrument.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Bounded, PII-free audit delivery metrics and alert-consumable backlog gauges. */
@Component
public class ExternalAuditMetrics {
    private final MeterRegistry registry;
    public ExternalAuditMetrics(MeterRegistry registry,JdbcTemplate jdbc){this.registry=registry;
        Gauge.builder("workflow.audit.outbox.pending",jdbc,j->count(j,"PENDING")+count(j,"RETRY")).register(registry);
        Gauge.builder("workflow.audit.outbox.dead",jdbc,j->count(j,"DEAD")).register(registry);
    }
    public void delivery(String outcome,long nanos){registry.counter("workflow.audit.delivery","outcome",outcome).increment();registry.timer("workflow.audit.delivery.latency","outcome",outcome).record(nanos,TimeUnit.NANOSECONDS);}
    public void receipt(String status){registry.counter("workflow.audit.receipt","status",status).increment();}
    public void replay(){registry.counter("workflow.audit.replay").increment();}
    private static double count(JdbcTemplate jdbc,String status){try{return jdbc.queryForObject("SELECT COUNT(*) FROM workflow_role_external_audit_outbox WHERE status=?",Long.class,status);}catch(Exception ignored){return 0;}}
}
