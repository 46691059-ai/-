package cn.gov.enterprise.modules.workflow.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaimWorkflowTaskCommand(
        @NotBlank @Size(max = 128) String idempotencyKey,
        @Size(max = 500) String remark,
        @Size(max = 64) String traceId) {
}
