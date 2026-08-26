package cn.gov.enterprise.modules.workflow.domain.canary;

import java.time.Instant;

@FunctionalInterface
public interface CanaryRuntimeGate {
    boolean allows(CanaryScope scope, Instant at);
}
