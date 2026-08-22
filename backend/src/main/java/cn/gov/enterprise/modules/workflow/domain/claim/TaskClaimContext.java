package cn.gov.enterprise.modules.workflow.domain.claim;

/** Non-sensitive facts used by Workflow Claim governance policies. */
public record TaskClaimContext(
        Long taskId, Long instanceId, Long nodeExecutionId,
        Long claimantUserId, Long claimantOrgId, Long initiatorUserId,
        Long initiatorOrgId, String businessType, String businessKey) {
}
