package cn.gov.enterprise.modules.workflow.domain.claim;

/** Business-neutral segregation-of-duties port. */
public interface SegregationOfDutiesPolicy {
    Decision evaluate(TaskClaimContext context);

    record Decision(boolean allowed, String policyCode, String policyVersion,
                    String reasonCode, String evidenceSummary) {
    }
}
