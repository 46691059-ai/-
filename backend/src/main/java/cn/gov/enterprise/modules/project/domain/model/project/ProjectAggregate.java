package cn.gov.enterprise.modules.project.domain.model.project;

import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleInstance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/** Framework-independent Project aggregate root used by the first repository migration. */
public final class ProjectAggregate {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final Long id;
    private final String projectNo;
    private final String projectName;
    private final String projectType;
    private final String projectMode;
    private final String sourceType;
    private final Long customerId;
    private final Long leaderId;
    private final Long responsibleOrgId;
    private final String status;
    private final LocalDate plannedStartDate;
    private final LocalDate plannedEndDate;
    private final LocalDate actualStartDate;
    private final LocalDate actualEndDate;
    private final BigDecimal budgetAmount;
    private final BigDecimal contractAmount;
    private final BigDecimal expectedIncome;
    private final BigDecimal expectedProfit;
    private final BigDecimal actualIncome;
    private final BigDecimal actualProfit;
    private final String currentStageCode;
    private final String riskLevel;
    private final BigDecimal progress;
    private final String description;
    private final String remark;
    private final String createBy;
    private final LocalDateTime createTime;
    private final LocalDateTime updateTime;
    private final LifecycleInstance lifecycle;
    private final int aggregateVersion;

    /** Compatibility constructor retained for the initial domain skeleton tests. */
    public ProjectAggregate(
            Long id,
            String projectNo,
            String projectName,
            String projectType,
            Long responsibleOrgId,
            Long leaderId,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            String status,
            String currentStageCode,
            BigDecimal progress,
            LifecycleInstance lifecycle,
            int aggregateVersion) {
        this(
                id, projectNo, projectName, projectType, null, null, null, leaderId,
                responsibleOrgId, status, plannedStartDate, plannedEndDate, null, null,
                ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, currentStageCode, "LOW", progress,
                null, null, null, null, null, lifecycle, aggregateVersion);
    }

    private ProjectAggregate(
            Long id,
            String projectNo,
            String projectName,
            String projectType,
            String projectMode,
            String sourceType,
            Long customerId,
            Long leaderId,
            Long responsibleOrgId,
            String status,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualStartDate,
            LocalDate actualEndDate,
            BigDecimal budgetAmount,
            BigDecimal contractAmount,
            BigDecimal expectedIncome,
            BigDecimal expectedProfit,
            BigDecimal actualIncome,
            BigDecimal actualProfit,
            String currentStageCode,
            String riskLevel,
            BigDecimal progress,
            String description,
            String remark,
            String createBy,
            LocalDateTime createTime,
            LocalDateTime updateTime,
            LifecycleInstance lifecycle,
            int aggregateVersion) {
        this.id = requirePositive(id, "Project id");
        this.projectNo = requireText(projectNo, "Project number");
        this.projectName = requireText(projectName, "Project name");
        this.projectType = requireText(projectType, "Project type");
        this.projectMode = normalize(projectMode);
        this.sourceType = normalize(sourceType);
        this.customerId = customerId;
        this.leaderId = requirePositive(leaderId, "Project leader id");
        this.responsibleOrgId = requirePositive(responsibleOrgId, "Responsible organization id");
        this.status = requireText(status, "Project status");
        validateDateRange(plannedStartDate, plannedEndDate, "Planned project dates");
        validateDateRange(actualStartDate, actualEndDate, "Actual project dates");
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
        this.actualStartDate = actualStartDate;
        this.actualEndDate = actualEndDate;
        this.budgetAmount = requireNonNegative(budgetAmount, "Budget amount");
        this.contractAmount = requireNonNegative(contractAmount, "Contract amount");
        this.expectedIncome = requireNonNegative(expectedIncome, "Expected income");
        this.expectedProfit = Objects.requireNonNull(expectedProfit, "Expected profit must not be null");
        this.actualIncome = requireNonNegative(actualIncome, "Actual income");
        this.actualProfit = Objects.requireNonNull(actualProfit, "Actual profit must not be null");
        this.currentStageCode = requireText(currentStageCode, "Current stage code");
        this.riskLevel = requireText(riskLevel, "Risk level");
        this.progress = requirePercentage(progress, "Project progress");
        this.description = description;
        this.remark = remark;
        this.createBy = createBy;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.lifecycle = Objects.requireNonNull(lifecycle, "Lifecycle must not be null");
        if (!id.equals(lifecycle.getProjectId())) {
            throw new IllegalArgumentException("Lifecycle must belong to the project");
        }
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("Aggregate version must not be negative");
        }
        this.aggregateVersion = aggregateVersion;
    }

    public static ProjectAggregate create(
            Long id,
            String projectNo,
            String projectName,
            String projectType,
            String projectMode,
            Long leaderId,
            Long responsibleOrgId,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            BigDecimal budgetAmount,
            BigDecimal expectedIncome,
            BigDecimal expectedProfit,
            String riskLevel,
            String remark,
            LifecycleInstance lifecycle) {
        String initialStageCode = lifecycle.getStages().stream()
                .min(java.util.Comparator.comparingInt(
                        stage -> stage.getSnapshot().getStageOrder()))
                .orElseThrow()
                .getSnapshot().getStageCode();
        return new ProjectAggregate(
                id, projectNo, projectName, projectType, projectMode, null, null, leaderId,
                responsibleOrgId, "RESERVED", plannedStartDate, plannedEndDate, null, null,
                budgetAmount, ZERO, expectedIncome, expectedProfit, ZERO, ZERO,
                initialStageCode, riskLevel, ZERO, null, remark, null, null, null, lifecycle, 0);
    }

    public static ProjectAggregate restore(
            Long id,
            String projectNo,
            String projectName,
            String projectType,
            String projectMode,
            String sourceType,
            Long customerId,
            Long leaderId,
            Long responsibleOrgId,
            String status,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualStartDate,
            LocalDate actualEndDate,
            BigDecimal budgetAmount,
            BigDecimal contractAmount,
            BigDecimal expectedIncome,
            BigDecimal expectedProfit,
            BigDecimal actualIncome,
            BigDecimal actualProfit,
            String currentStageCode,
            String riskLevel,
            BigDecimal progress,
            String description,
            String remark,
            String createBy,
            LocalDateTime createTime,
            LocalDateTime updateTime,
            LifecycleInstance lifecycle,
            int aggregateVersion) {
        return new ProjectAggregate(
                id, projectNo, projectName, projectType, projectMode, sourceType, customerId,
                leaderId, responsibleOrgId, status, plannedStartDate, plannedEndDate,
                actualStartDate, actualEndDate, budgetAmount, contractAmount, expectedIncome,
                expectedProfit, actualIncome, actualProfit, currentStageCode, riskLevel, progress,
                description, remark, createBy, createTime, updateTime, lifecycle, aggregateVersion);
    }

    public Long getId() { return id; }
    public String getProjectNo() { return projectNo; }
    public String getProjectName() { return projectName; }
    public String getProjectType() { return projectType; }
    public String getProjectMode() { return projectMode; }
    public String getSourceType() { return sourceType; }
    public Long getCustomerId() { return customerId; }
    public Long getLeaderId() { return leaderId; }
    public Long getResponsibleOrgId() { return responsibleOrgId; }
    public String getStatus() { return status; }
    public LocalDate getPlannedStartDate() { return plannedStartDate; }
    public LocalDate getPlannedEndDate() { return plannedEndDate; }
    public LocalDate getActualStartDate() { return actualStartDate; }
    public LocalDate getActualEndDate() { return actualEndDate; }
    public BigDecimal getBudgetAmount() { return budgetAmount; }
    public BigDecimal getContractAmount() { return contractAmount; }
    public BigDecimal getExpectedIncome() { return expectedIncome; }
    public BigDecimal getExpectedProfit() { return expectedProfit; }
    public BigDecimal getActualIncome() { return actualIncome; }
    public BigDecimal getActualProfit() { return actualProfit; }
    public String getCurrentStageCode() { return currentStageCode; }
    public String getRiskLevel() { return riskLevel; }
    public BigDecimal getProgress() { return progress; }
    public String getDescription() { return description; }
    public String getRemark() { return remark; }
    public String getCreateBy() { return createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public LifecycleInstance getLifecycle() { return lifecycle; }
    public int getAggregateVersion() { return aggregateVersion; }

    private static void validateDateRange(LocalDate start, LocalDate end, String label) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException(label + " are invalid");
        }
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
        return value;
    }

    private static BigDecimal requirePercentage(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(label + " must be between 0 and 100");
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
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
