package cn.gov.enterprise.modules.project.domain.model.task;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable state of a project-scoped task sub-aggregate. */
public final class ProjectTask {
    private final Long id;
    private final Long projectId;
    private final Long stageId;
    private final Long parentTaskId;
    private final String taskNo;
    private final String taskName;
    private final String taskContent;
    private final Long responsiblePersonId;
    private final LocalDate planDate;
    private final LocalDate planStart;
    private final LocalDate planEnd;
    private final LocalDate actualDate;
    private final LocalDate actualStart;
    private final LocalDate actualEnd;
    private final String status;
    private final String priority;
    private final BigDecimal progress;
    private final int sortNo;
    private final String remark;
    private final int aggregateVersion;

    public ProjectTask(
            Long id, Long projectId, Long stageId, Long parentTaskId,
            String taskNo, String taskName, String taskContent, Long responsiblePersonId,
            LocalDate planDate, LocalDate planStart, LocalDate planEnd,
            LocalDate actualDate, LocalDate actualStart, LocalDate actualEnd,
            String status, String priority, BigDecimal progress, int sortNo,
            String remark, int aggregateVersion) {
        this.id = requirePositive(id, "Task id");
        this.projectId = requirePositive(projectId, "Project id");
        this.stageId = requirePositive(stageId, "Stage id");
        this.parentTaskId = parentTaskId;
        if (id.equals(parentTaskId)) {
            throw new IllegalArgumentException("Task cannot be its own parent");
        }
        this.taskNo = requireText(taskNo, "Task number");
        this.taskName = requireText(taskName, "Task name");
        this.taskContent = taskContent;
        this.responsiblePersonId = responsiblePersonId;
        validateRange(planStart, planEnd, "Task plan dates");
        validateRange(actualStart, actualEnd, "Task actual dates");
        this.planDate = planDate;
        this.planStart = planStart;
        this.planEnd = planEnd;
        this.actualDate = actualDate;
        this.actualStart = actualStart;
        this.actualEnd = actualEnd;
        this.status = requireText(status, "Task status");
        this.priority = requireText(priority, "Task priority");
        this.progress = requireProgress(progress);
        this.sortNo = sortNo;
        this.remark = remark;
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("Task version must not be negative");
        }
        this.aggregateVersion = aggregateVersion;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getStageId() { return stageId; }
    public Long getParentTaskId() { return parentTaskId; }
    public String getTaskNo() { return taskNo; }
    public String getTaskName() { return taskName; }
    public String getTaskContent() { return taskContent; }
    public Long getResponsiblePersonId() { return responsiblePersonId; }
    public LocalDate getPlanDate() { return planDate; }
    public LocalDate getPlanStart() { return planStart; }
    public LocalDate getPlanEnd() { return planEnd; }
    public LocalDate getActualDate() { return actualDate; }
    public LocalDate getActualStart() { return actualStart; }
    public LocalDate getActualEnd() { return actualEnd; }
    public String getStatus() { return status; }
    public String getPriority() { return priority; }
    public BigDecimal getProgress() { return progress; }
    public int getSortNo() { return sortNo; }
    public String getRemark() { return remark; }
    public int getAggregateVersion() { return aggregateVersion; }

    private static void validateRange(LocalDate start, LocalDate end, String label) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException(label + " are invalid");
        }
    }

    private static BigDecimal requireProgress(BigDecimal value) {
        Objects.requireNonNull(value, "Task progress must not be null");
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Task progress must be between 0 and 100");
        }
        return value;
    }

    private static Long requirePositive(Long value, String label) {
        if (value == null || value <= 0) throw new IllegalArgumentException(label + " must be positive");
        return value;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value.trim();
    }
}
