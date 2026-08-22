package cn.gov.enterprise.modules.workflow.domain.role.promotion;

/** Governance state only. No state in this model enables ROLE Runtime. */
public enum RuntimeBindingPromotionStatus {
    CREATED,
    VALIDATING,
    ELIGIBLE,
    APPROVED,
    PROMOTED,
    BLOCKED,
    REJECTED,
    REVOKED
}
