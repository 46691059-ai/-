package cn.gov.enterprise.modules.workflow.application.command;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PublishWorkflowVersionCommand(
        @PositiveOrZero int expectedDefinitionVersion,
        @PositiveOrZero int expectedVersion,
        @Size(max = 500) String reason) {}
