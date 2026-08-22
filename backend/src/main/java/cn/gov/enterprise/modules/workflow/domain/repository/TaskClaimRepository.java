package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaim;
import java.util.Optional;

public interface TaskClaimRepository {
    Optional<TaskClaim> findByTaskIdAndIdempotencyKey(Long taskId, String idempotencyKey);
    Optional<TaskClaim> findActiveByTaskId(Long taskId);

    /** Inserts one immutable Claim ownership fact and returns that confirmed fact. */
    TaskClaim insert(TaskClaim claim);
}
