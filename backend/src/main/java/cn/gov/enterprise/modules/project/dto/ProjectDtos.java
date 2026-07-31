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
            @NotBlank @Size(max = 64) String projectNo,
            @NotBlank @Size(max = 200) String projectName,
            @NotBlank @Pattern(regexp = "01|02|03|04|05|06")
            String projectType,
            @Size(max = 32) String projectMode,
            @NotNull Long leaderId,
            @NotNull Long departmentId,
            LocalDate startDate,
            LocalDate endDate,
            @NotNull @DecimalMin("0.00") BigDecimal budgetAmount,
            @NotNull @DecimalMin("0.00") BigDecimal expectedIncome,
            @NotNull BigDecimal expectedProfit,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String riskLevel,
            @Size(max = 500) String remark) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String projectName,
            @NotBlank @Pattern(regexp = "01|02|03|04|05|06")
            String projectType,
            @Size(max = 32) String projectMode,
            @NotNull Long leaderId,
            @NotNull Long departmentId,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate actualStartDate,
            LocalDate actualEndDate,
            @NotNull @DecimalMin("0.00") BigDecimal budgetAmount,
            @NotNull @DecimalMin("0.00") BigDecimal expectedIncome,
            @NotNull BigDecimal expectedProfit,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String riskLevel,
            @Size(max = 500) String remark,
            @NotNull Integer version) {
    }

    public record Response(
            Long id, String projectNo, String projectName, String projectType, String projectMode,
            Long leaderId, Long departmentId, String status, LocalDate startDate, LocalDate endDate,
            LocalDate actualStartDate, LocalDate actualEndDate, BigDecimal budgetAmount,
            BigDecimal expectedIncome, BigDecimal expectedProfit, String currentStageCode,
            String riskLevel, BigDecimal progress, String remark,
            LocalDateTime createTime, LocalDateTime updateTime, Integer version) {
    }

    public record DetailResponse(
            Response project, List<StageResponse> stages, List<TaskResponse> tasks,
            long taskTotal, List<MemberResponse> members, long memberTotal) {
    }

    public record StageUpdateRequest(
            Long responsiblePerson,
            LocalDate startTime,
            LocalDate endTime,
            LocalDate actualStartTime,
            LocalDate actualEndTime,
            @NotBlank @Pattern(regexp = "NOT_STARTED|IN_PROGRESS|COMPLETED|SKIPPED") String status,
            @NotBlank @Pattern(regexp = "NOT_SUBMITTED|PENDING|APPROVED|REJECTED")
            String approvalStatus,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal completionPercent,
            @Size(max = 500) String remark,
            @NotNull Integer version) {
    }

    public record StageResponse(
            Long id, Long projectId, String stageCode, String stageName, Integer stageOrder,
            LocalDate startTime, LocalDate endTime, LocalDate actualStartTime,
            LocalDate actualEndTime, String status, Long responsiblePerson,
            String approvalStatus, BigDecimal completionPercent, String remark, Integer version) {
    }

    public record TaskRequest(
            @NotNull Long stageId,
            Long parentTaskId,
            @NotBlank @Size(max = 64) String taskNo,
            @NotBlank @Size(max = 200) String taskName,
            Long responsiblePerson,
            LocalDate planDate,
            LocalDate actualDate,
            @NotBlank @Pattern(regexp = "TODO|IN_PROGRESS|BLOCKED|COMPLETED|CANCELLED") String status,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal progress,
            Integer sortNo,
            @Size(max = 500) String remark,
            Integer version) {
    }

    public record TaskResponse(
            Long id, Long projectId, Long stageId, Long parentTaskId, String taskNo,
            String taskName, Long responsiblePerson, LocalDate planDate, LocalDate actualDate,
            String status, String priority, BigDecimal progress, Integer sortNo,
            String remark, Integer version) {
    }

    public record MemberRequest(
            @NotNull Long employeeId,
            @NotBlank @Pattern(regexp = "MANAGER|CORE|PARTICIPANT|EXPERT") String role,
            @Size(max = 1000) String responsibilities,
            @NotNull LocalDate joinedDate,
            LocalDate leftDate,
            @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
            @Size(max = 500) String remark,
            Integer version) {
    }

    public record MemberResponse(
            Long id, Long projectId, Long employeeId, String role, String responsibilities,
            LocalDate joinedDate, LocalDate leftDate, String status, String remark, Integer version) {
    }
}
