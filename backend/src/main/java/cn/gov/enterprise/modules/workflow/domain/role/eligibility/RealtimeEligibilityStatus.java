package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

/** Fail-closed, three-state outcome for one frozen ROLE candidate. */
public enum RealtimeEligibilityStatus {
    ELIGIBLE,
    INELIGIBLE,
    INDETERMINATE
}
