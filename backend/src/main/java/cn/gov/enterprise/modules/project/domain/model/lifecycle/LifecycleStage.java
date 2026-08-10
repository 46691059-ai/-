package cn.gov.enterprise.modules.project.domain.model.lifecycle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Runtime lifecycle stage owned by a project lifecycle instance. */
public final class LifecycleStage {
    private final Long id;
    private final Long projectId;
    private final StageSnapshot snapshot;
    private final LocalDate plannedStartDate;
    private final LocalDate plannedEndDate;
    private final LocalDate actualStartDate;
    private final LocalDate actualEndDate;
    private final String status;
    private final Long responsiblePersonId;
    private final String approvalStatus;
    private final BigDecimal completionPercent;
    private final String remark;
    private final int version;

    public LifecycleStage(
            Long id,
            Long projectId,
            StageSnapshot snapshot,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualStartDate,
            LocalDate actualEndDate,
            String status,
            Long responsiblePersonId,
            String approvalStatus,
            BigDecimal completionPercent,
            String remark,
            int version) {
        this.id = requirePositive(id, "Stage id");
        this.projectId = requirePositive(projectId, "Project id");
        this.snapshot = Objects.requireNonNull(snapshot, "Stage snapshot must not be null");
        validateRange(plannedStartDate, plannedEndDate, "Planned stage dates");
        validateRange(actualStartDate, actualEndDate, "Actual stage dates");
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
        this.actualStartDate = actualStartDate;
        this.actualEndDate = actualEndDate;
        this.status = requireText(status, "Stage status");
        this.responsiblePersonId = responsiblePersonId;
        this.approvalStatus = requireText(approvalStatus, "Approval status");
        this.completionPercent = requirePercentage(completionPercent);
        this.remark = remark;
        if (version < 0) {
            throw new IllegalArgumentException("Stage version must not be negative");
        }
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public StageSnapshot getSnapshot() {
        return snapshot;
    }

    public LocalDate getPlannedStartDate() {
        return plannedStartDate;
    }

    public LocalDate getPlannedEndDate() {
        return plannedEndDate;
    }

    public LocalDate getActualStartDate() {
        return actualStartDate;
    }

    public LocalDate getActualEndDate() {
        return actualEndDate;
    }

    public String getStatus() {
        return status;
    }

    public Long getResponsiblePersonId() {
        return responsiblePersonId;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public BigDecimal getCompletionPercent() {
        return completionPercent;
    }

    public String getRemark() {
        return remark;
    }

    public int getVersion() {
        return version;
    }

    private static void validateRange(LocalDate start, LocalDate end, String label) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException(label + " are invalid");
        }
    }

    private static BigDecimal requirePercentage(BigDecimal value) {
        Objects.requireNonNull(value, "Stage completion must not be null");
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Stage completion must be between 0 and 100");
        }
        return value;
    }

    private static Long requirePositive(Long value, String label) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
