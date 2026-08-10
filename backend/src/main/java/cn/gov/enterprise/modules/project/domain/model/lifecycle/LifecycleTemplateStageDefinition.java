package cn.gov.enterprise.modules.project.domain.model.lifecycle;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable stage definition loaded from one active lifecycle template version. */
public record LifecycleTemplateStageDefinition(
        Long id,
        String stageCode,
        String stageName,
        int stageOrder,
        BigDecimal progressWeight,
        boolean required,
        boolean allowSkip,
        Integer plannedDurationDays,
        boolean autoStart,
        boolean approvalRequired,
        String approvalSceneCode,
        String completionMode) {

    public LifecycleTemplateStageDefinition {
        if (id == null || id <= 0) throw new IllegalArgumentException("Stage template id must be positive");
        stageCode = requireText(stageCode, "Stage code");
        stageName = requireText(stageName, "Stage name");
        if (stageOrder <= 0) throw new IllegalArgumentException("Stage order must be positive");
        Objects.requireNonNull(progressWeight, "Stage weight must not be null");
        if (progressWeight.signum() < 0 || progressWeight.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Stage weight must be between 0 and 100");
        }
        if (plannedDurationDays != null && plannedDurationDays < 0) {
            throw new IllegalArgumentException("Planned duration must not be negative");
        }
        completionMode = requireText(completionMode, "Completion mode");
        if (approvalRequired && (approvalSceneCode == null || approvalSceneCode.isBlank())) {
            throw new IllegalArgumentException("Approval scene is required for an approval stage");
        }
    }

    public StageSnapshot snapshot() {
        return new StageSnapshot(
                id, stageCode, stageName, stageOrder, progressWeight,
                required, allowSkip, approvalRequired, approvalSceneCode,
                plannedDurationDays, completionMode);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
