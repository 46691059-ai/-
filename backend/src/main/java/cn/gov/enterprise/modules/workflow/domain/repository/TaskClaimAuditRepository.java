package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaim;

/** Append-only successful Claim audit persistence. Rejections use platform security audit. */
public interface TaskClaimAuditRepository {
    /** Appends a SUCCESS event derived exclusively from an already persisted Claim. */
    void appendSuccess(Long auditId, TaskClaim persistedClaim);
}
