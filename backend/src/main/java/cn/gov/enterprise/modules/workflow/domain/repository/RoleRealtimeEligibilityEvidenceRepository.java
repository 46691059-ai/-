package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.RoleRealtimeEligibilityPersistenceBundle;
import java.util.Optional;

public interface RoleRealtimeEligibilityEvidenceRepository {
    Optional<RoleRealtimeEligibilityPersistenceBundle.Header> findByRequestId(String requestId);
    Optional<RoleRealtimeEligibilityPersistenceBundle.Header> findByClaimAttempt(
            long taskId, long candidateUserId, String claimRequestId, int attemptNo);
    Optional<RoleRealtimeEligibilityPersistenceBundle.Header> findByIdForUpdate(long evidenceId);
    Optional<RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent> findLatestEvent(long evidenceId);
    void insert(RoleRealtimeEligibilityPersistenceBundle bundle);
    void appendEvent(RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent event);
}
