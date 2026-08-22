package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Minimal framework breaker. Production thresholds remain deployment-owned configuration. */
public final class InMemoryRoleDirectoryCircuitBreaker implements RoleDirectoryCircuitBreaker {
    private final int failureThreshold;
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    public InMemoryRoleDirectoryCircuitBreaker(int failureThreshold) {
        if (failureThreshold <= 0) throw new IllegalArgumentException("failureThreshold must be positive");
        this.failureThreshold = failureThreshold;
    }
    public State state() { return state.get(); }
    public boolean allowRequest() { return state.get() != State.OPEN; }
    public void recordSuccess() { failures.set(0); state.set(State.CLOSED); }
    public void recordFailure(DirectoryFailure failure) {
        if (failure.code().retryable() && failures.incrementAndGet() >= failureThreshold) state.set(State.OPEN);
    }
    public void halfOpenForProbe() { state.compareAndSet(State.OPEN, State.HALF_OPEN); }
}
