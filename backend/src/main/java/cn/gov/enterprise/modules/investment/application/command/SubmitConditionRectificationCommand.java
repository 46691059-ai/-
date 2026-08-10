package cn.gov.enterprise.modules.investment.application.command;

import jakarta.validation.constraints.NotBlank;

public record SubmitConditionRectificationCommand(
        @NotBlank String rectificationSummary,
        Long evidenceFileId,
        String idempotencyKey) {}
