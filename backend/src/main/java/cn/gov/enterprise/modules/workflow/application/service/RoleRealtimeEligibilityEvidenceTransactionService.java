package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRealtimeEligibilityEvidenceRepository;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.RoleRealtimeEligibilityPersistenceBundle;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.RoleRealtimeEligibilityPersistenceCanonical;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal transaction boundary; it persists proof but never creates a Task, Pool or Claim. */
@Service
public class RoleRealtimeEligibilityEvidenceTransactionService {
    private final RoleRealtimeEligibilityEvidenceRepository repository;
    public RoleRealtimeEligibilityEvidenceTransactionService(RoleRealtimeEligibilityEvidenceRepository repository) {
        this.repository=repository;
    }

    @Transactional
    public RoleRealtimeEligibilityPersistenceBundle.Header persist(RoleRealtimeEligibilityPersistenceBundle bundle) {
        var h=bundle.header();
        var existing=repository.findByRequestId(h.eligibilityRequestId());
        if(existing.isPresent()) return sameOrReject(existing.get(),h);
        existing=repository.findByClaimAttempt(h.taskId(),h.candidateUserId(),h.claimRequestId(),h.attemptNo());
        if(existing.isPresent()) return sameOrReject(existing.get(),h);
        RoleRealtimeEligibilityPersistenceCanonical.verify(bundle);
        repository.insert(bundle);
        return h;
    }

    private RoleRealtimeEligibilityPersistenceBundle.Header sameOrReject(
            RoleRealtimeEligibilityPersistenceBundle.Header existing,
            RoleRealtimeEligibilityPersistenceBundle.Header requested) {
        if(!existing.persistenceHash().equals(requested.persistenceHash())) {
            throw new BusinessException("B26163","eligibility evidence idempotency payload mismatch");
        }
        return existing;
    }
}
