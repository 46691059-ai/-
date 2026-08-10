package cn.gov.enterprise.modules.investment.domain.model;

/** Maps external Workflow facts to canonical Investment decision states. */
public final class DecisionWorkflowEventPolicy {
    public Outcome evaluate(String eventType, String result) {
        return switch (eventType) {
            case "PROCESS_STARTED", "APPROVAL_STARTED", "NODE_ACTIVATED", "NODE_COMPLETED" ->
                    new Outcome(InvestmentDecisionCase.Status.IN_APPROVAL, "RUNNING", false);
            case "APPROVAL_APPROVED" ->
                    new Outcome(InvestmentDecisionCase.Status.APPROVED, "COMPLETED", false);
            case "APPROVAL_REJECTED" ->
                    new Outcome(InvestmentDecisionCase.Status.REJECTED, "COMPLETED", false);
            case "PROCESS_WITHDRAWN", "PROCESS_CANCELLED" ->
                    new Outcome(InvestmentDecisionCase.Status.WITHDRAWN,
                            "PROCESS_CANCELLED".equals(eventType) ? "CANCELLED" : "WITHDRAWN", false);
            case "PROCESS_TERMINATED", "PROCESS_EXCEPTION", "WORKFLOW_EXCEPTION" ->
                    new Outcome(null, "TERMINATED", true);
            case "PROCESS_RETURNED" ->
                    new Outcome(InvestmentDecisionCase.Status.REJECTED, "RETURNED", false);
            case "PROCESS_COMPLETED" -> completed(result);
            default -> new Outcome(null, "RUNNING", true);
        };
    }

    private static Outcome completed(String result) {
        return switch (String.valueOf(result)) {
            case "APPROVED", "APPROVED_WITH_CONDITIONS" ->
                    new Outcome(InvestmentDecisionCase.Status.APPROVED, "COMPLETED", false);
            case "REJECTED" -> new Outcome(InvestmentDecisionCase.Status.REJECTED, "COMPLETED", false);
            case "WITHDRAWN" -> new Outcome(InvestmentDecisionCase.Status.WITHDRAWN, "WITHDRAWN", false);
            default -> new Outcome(null, "TERMINATED", true);
        };
    }

    public record Outcome(
            InvestmentDecisionCase.Status decisionStatus,
            String workflowStatus,
            boolean exceptional) {}
}
