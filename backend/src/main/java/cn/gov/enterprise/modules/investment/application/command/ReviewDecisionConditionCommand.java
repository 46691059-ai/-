package cn.gov.enterprise.modules.investment.application.command;

import jakarta.validation.constraints.NotNull;

public record ReviewDecisionConditionCommand(
        @NotNull ReviewResult result,
        String opinion,
        String idempotencyKey) {
    public enum ReviewResult { APPROVED, REJECTED }
}
