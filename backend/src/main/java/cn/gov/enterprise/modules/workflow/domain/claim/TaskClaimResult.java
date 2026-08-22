package cn.gov.enterprise.modules.workflow.domain.claim;

import java.time.LocalDateTime;

public record TaskClaimResult(
        Long taskId, Long claimId, Long assigneeUserId, LocalDateTime claimTime,
        String taskStatus, String poolStatus, boolean idempotentReplay) {

    public static TaskClaimResult from(TaskClaim claim, boolean replay) {
        return new TaskClaimResult(claim.taskId(), claim.id(), claim.candidateUserId(),
                claim.claimTime(), claim.taskStatusAfter(), claim.poolStatusAfter(), replay);
    }
}
