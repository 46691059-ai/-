package cn.gov.enterprise.modules.investment.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInvestmentDecisionCommand(
        @NotNull Long investmentId,
        @NotBlank String decisionNo,
        @NotBlank String subject) {}
