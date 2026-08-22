package cn.gov.enterprise.modules.workflow.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProcessWorkflowTaskCommand(
        @Size(max = 1000) String comment,
        @NotBlank @Size(max = 200) String idempotencyKey) {}
