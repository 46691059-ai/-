package cn.gov.enterprise.modules.workflow.infrastructure.directory;

public interface RoleDirectoryCircuitBreaker {
    enum State { CLOSED, OPEN, HALF_OPEN }
    State state();
    boolean allowRequest();
    void recordSuccess();
    void recordFailure(DirectoryFailure failure);
}
