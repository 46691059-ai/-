package cn.gov.enterprise.modules.workflow.domain.claim;

/** Claim lifecycle; WF4.2 implements only the CLAIMED transition. */
public enum TaskClaimStatus {
    CLAIMED,
    RELEASED,
    CANCELLED
}
