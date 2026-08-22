package cn.gov.enterprise.modules.workflow.domain.claim;

/** Port for current personnel facts. Implementations must fail closed. */
public interface RealtimeEligibilityChecker {
    EligibilityDecision check(TaskClaimContext context);

    record EligibilityDecision(
            boolean allowed, boolean userActive, boolean employmentActive,
            boolean organizationActive, boolean positionActive,
            String reasonCode, String evidenceSummary) {
    }
}
