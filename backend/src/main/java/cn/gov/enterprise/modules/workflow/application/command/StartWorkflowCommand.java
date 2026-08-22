package cn.gov.enterprise.modules.workflow.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StartWorkflowCommand(
        @NotNull Long definitionId,
        @NotBlank @Size(max = 64) String businessType,
        @NotBlank @Size(max = 100) String businessId,
        @NotBlank @Size(max = 200) String businessKey,
        @NotNull Long enterpriseId,
        @Size(max = 100) String snapshotRef,
        @Size(max = 128) String snapshotHash,
        @Positive int attemptNo,
        @Size(max = 1000000) String variablesSnapshot,
        @NotBlank @Size(max = 200) String idempotencyKey) {}
