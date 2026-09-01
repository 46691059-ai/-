package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.canary.CanaryGovernanceRecord;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryScope;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryGovernanceState;
import java.time.Instant;
import java.util.Optional;

public interface CanaryGovernanceRepository {
    void insert(CanaryGovernanceRecord record);
    Optional<CanaryGovernanceRecord> latest(CanaryScope scope, Instant at);
    default long countByExactScopeAndState(CanaryScope scope, CanaryGovernanceState state) { return -1; }
}
