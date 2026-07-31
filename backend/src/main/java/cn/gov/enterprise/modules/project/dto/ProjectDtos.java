package cn.gov.enterprise.modules.project.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 64) String projectCode,
            @NotBlank @Size(max = 200) String projectName,
            @NotBlank @Pattern(regexp = "INVESTMENT|ENGINEERING|DIGITAL|OPERATION|OTHER")
            String projectType,
            @NotNull Long orgId,
            @NotNull Long managerUserId,
            @Size(max = 2000) String description,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            @NotNull @DecimalMin("0.00") BigDecimal investmentAmount,
            @NotNull @DecimalMin("0.00") BigDecimal expectedIncome,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String riskLevel) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String projectName,
            @NotBlank @Pattern(regexp = "INVESTMENT|ENGINEERING|DIGITAL|OPERATION|OTHER")
            String projectType,
            @NotNull Long orgId,
            @NotNull Long managerUserId,
            @Size(max = 2000) String description,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualStartDate,
            LocalDate actualEndDate,
            @NotNull @DecimalMin("0.00") BigDecimal investmentAmount,
            @NotNull @DecimalMin("0.00") BigDecimal expectedIncome,
            @NotNull @DecimalMin("0.00") BigDecimal actualIncome,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String riskLevel,
            @NotNull Integer version) {
    }

    public record Response(
            Long id,
            String projectCode,
            String projectName,
            String projectType,
            Long orgId,
            Long managerUserId,
            String description,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualStartDate,
            LocalDate actualEndDate,
            BigDecimal investmentAmount,
            BigDecimal expectedIncome,
            BigDecimal actualIncome,
            String currentStageCode,
            String projectStatus,
            String riskLevel,
            BigDecimal progress,
            LocalDateTime createdTime,
            LocalDateTime updatedTime,
            Integer version) {
    }

    public record DetailResponse(
            Response project,
            List<StageResponse> stages,
            List<TaskResponse> tasks,
            long taskTotal,
            List<MemberResponse> members,
            long memberTotal) {
    }

    public record StageUpdateRequest(
            Long ownerUserId,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualStartDate,
            LocalDate actualEndDate,
            @NotBlank @Pattern(regexp = "NOT_STARTED|IN_PROGRESS|COMPLETED|SKIPPED")
            String stageStatus,
            @NotBlank @Pattern(regexp = "NOT_SUBMITTED|PENDING|APPROVED|REJECTED")
            String approvalStatus,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal completionPercent,
            @Size(max = 1000) String milestoneDesc,
            @Size(max = 1000) String riskSummary,
            @NotNull Integer version) {
    }

    public record StageResponse(
            Long id,
            Long projectId,
            String stageCode,
            String stageName,
            Integer stageOrder,
            String stageStatus,
            Long ownerUserId,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualStartDate,
            LocalDate actualEndDate,
            String approvalStatus,
            BigDecimal completionPercent,
            String milestoneDesc,
            String riskSummary,
            Integer version) {
    }

    public record TaskRequest(
            @NotNull Long stageId,
            Long parentTaskId,
            @NotBlank @Size(max = 64) String taskCode,
            @NotBlank @Size(max = 200) String taskName,
            @NotBlank @Size(max = 32) String taskType,
            Long assigneeUserId,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
            @NotBlank @Pattern(regexp = "TODO|IN_PROGRESS|BLOCKED|COMPLETED|CANCELLED")
            String taskStatus,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualStartDate,
            LocalDate actualEndDate,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal progress,
            @Size(max = 1000) String outputDesc,
            @Size(max = 1000) String riskDesc,
            Integer sortNo,
            Integer version) {
    }

    public record TaskResponse(
            Long id,
            Long projectId,
            Long stageId,
            Long parentTaskId,
            String taskCode,
            String taskName,
            String taskType,
            Long assigneeUserId,
            String priority,
            String taskStatus,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualStartDate,
            LocalDate actualEndDate,
            BigDecimal progress,
            String outputDesc,
            String riskDesc,
            Integer sortNo,
            Integer version) {
    }

    public record MemberRequest(
            @NotNull Long userId,
            @NotBlank @Pattern(regexp = "MANAGER|CORE|PARTICIPANT|EXPERT") String memberRole,
            @Size(max = 1000) String responsibilities,
            @NotNull LocalDate joinedDate,
            LocalDate leftDate,
            @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String memberStatus,
            Integer version) {
    }

    public record MemberResponse(
            Long id,
            Long projectId,
            Long userId,
            String memberRole,
            String responsibilities,
            LocalDate joinedDate,
            LocalDate leftDate,
            String memberStatus,
            Integer version) {
    }
}
