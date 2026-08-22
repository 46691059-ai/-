package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import java.time.Duration;

@FunctionalInterface
public interface RoleDirectoryMetricsPort {
    enum Metric {
        REQUEST_COUNT, SUCCESS_COUNT, FAILURE_COUNT, LATENCY, TIMEOUT_COUNT, RETRY_COUNT,
        PARTIAL_COUNT, REVISION_MISMATCH, HASH_MISMATCH, CANDIDATE_COUNT, CIRCUIT_STATE
    }

    void record(String outcome, Duration latency, int candidateCount);
    default void recordEvent(Metric metric, long value) { }
    static RoleDirectoryMetricsPort noop() { return (outcome, latency, candidateCount) -> { }; }
}
