package cn.gov.enterprise.modules.workflow.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkflowVersionCommand(
        @NotBlank @Size(max = 30) String schemaVersion,
        @Size(max = 1000) String changeNote,
        Long sourceVersionId) {}
