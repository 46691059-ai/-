package cn.gov.enterprise.modules.investment.application.command;

/** Review decision for the current opportunity gate. */
public record ReviewInvestmentOpportunityCommand(
        Decision decision,
        String conclusion) {

    public enum Decision {
        APPROVE,
        REJECT
    }
}
