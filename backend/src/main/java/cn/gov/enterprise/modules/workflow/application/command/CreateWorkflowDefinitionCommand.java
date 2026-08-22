package cn.gov.enterprise.modules.workflow.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorkflowDefinitionCommand(
        @NotBlank @Size(max = 100) String definitionCode,
        @NotBlank @Size(max = 200) String definitionName,
        @NotBlank @Size(max = 64) String businessType,
        @NotNull Long enterpriseId,
        Long ownerOrgId,
        @Size(max = 1000) String description) {}
