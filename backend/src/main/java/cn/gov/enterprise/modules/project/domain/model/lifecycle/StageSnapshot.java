package cn.gov.enterprise.modules.project.domain.model.lifecycle;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable lifecycle stage definition copied when a project is created.
 *
 * <p>This model deliberately contains no persistence annotations. Runtime stage state remains in
 * the existing compatibility model until the lifecycle migration is implemented.</p>
 */
public final class StageSnapshot {
    private final Long sourceTemplateStageId;
    private final String stageCode;
    private final String stageName;
    private final int stageOrder;
    private final BigDecimal progressWeight;
    private final boolean required;
    private final boolean allowSkip;
    private final boolean approvalRequired;
    private final String approvalSceneCode;
    private final Integer plannedDurationDays;
    private final String completionMode;

    public StageSnapshot(
            Long sourceTemplateStageId,
            String stageCode,
            String stageName,
            int stageOrder,
            BigDecimal progressWeight,
            boolean required,
            boolean allowSkip,
            boolean approvalRequired,
            String approvalSceneCode) {
        this(sourceTemplateStageId, stageCode, stageName, stageOrder, progressWeight,
                required, allowSkip, approvalRequired, approvalSceneCode, null, "MANUAL");
    }

    public StageSnapshot(
            Long sourceTemplateStageId,
            String stageCode,
            String stageName,
            int stageOrder,
            BigDecimal progressWeight,
            boolean required,
            boolean allowSkip,
            boolean approvalRequired,
            String approvalSceneCode,
            Integer plannedDurationDays,
            String completionMode) {
        if (stageOrder <= 0) {
            throw new IllegalArgumentException("Stage order must be positive");
        }
        this.sourceTemplateStageId = sourceTemplateStageId;
        this.stageCode = requireText(stageCode, "Stage code");
        this.stageName = requireText(stageName, "Stage name");
        this.stageOrder = stageOrder;
        this.progressWeight = requirePercentage(progressWeight, "Stage weight");
        this.required = required;
        this.allowSkip = allowSkip;
        this.approvalRequired = approvalRequired;
        this.approvalSceneCode = normalize(approvalSceneCode);
        if (plannedDurationDays != null && plannedDurationDays < 0) {
            throw new IllegalArgumentException("Planned duration must not be negative");
        }
        this.plannedDurationDays = plannedDurationDays;
        this.completionMode = requireText(completionMode, "Completion mode");
        if (approvalRequired && this.approvalSceneCode == null) {
            throw new IllegalArgumentException("Approval scene is required for an approval stage");
        }
    }

    public Long getSourceTemplateStageId() {
        return sourceTemplateStageId;
    }

    public String getStageCode() {
        return stageCode;
    }

    public String getStageName() {
        return stageName;
    }

    public int getStageOrder() {
        return stageOrder;
    }

    public BigDecimal getProgressWeight() {
        return progressWeight;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isAllowSkip() {
        return allowSkip;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public String getApprovalSceneCode() {
        return approvalSceneCode;
    }

    public Integer getPlannedDurationDays() {
        return plannedDurationDays;
    }

    public String getCompletionMode() {
        return completionMode;
    }

    private static BigDecimal requirePercentage(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(label + " must be between 0 and 100");
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
