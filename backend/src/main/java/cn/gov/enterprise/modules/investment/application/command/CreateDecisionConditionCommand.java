package cn.gov.enterprise.modules.investment.application.command;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateDecisionConditionCommand(
        @NotNull Long sourceNodeId,
        @NotBlank String conditionNo,
        @NotBlank String content,
        boolean blocking,
        @NotNull Long responsibleOrgId,
        @NotNull Long responsiblePersonId,
        @NotNull @FutureOrPresent LocalDate deadline,
        String riskLevel) {}
