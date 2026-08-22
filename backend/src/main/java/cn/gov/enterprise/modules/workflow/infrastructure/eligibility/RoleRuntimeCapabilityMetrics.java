package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Bounded, PII-free production capability telemetry. */
@Component
public final class RoleRuntimeCapabilityMetrics {
    private final MeterRegistry registry;
    public RoleRuntimeCapabilityMetrics(MeterRegistry registry){this.registry=registry;}
    public <T> T observe(String capability,Supplier<T> call,Function<T,String> outcome){
        Timer.Sample sample=Timer.start(registry);
        try{T value=call.get();registry.counter("workflow.role_runtime.capability", "capability",capability,"outcome",outcome.apply(value)).increment();return value;}
        catch(RuntimeException ex){registry.counter("workflow.role_runtime.capability","capability",capability,"outcome","ERROR").increment();throw ex;}
        finally{sample.stop(registry.timer("workflow.role_runtime.capability.latency","capability",capability));}
    }
    public void claim(String outcome){registry.counter("workflow.role_runtime.claim","outcome",outcome).increment();}
}
