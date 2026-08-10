package cn.gov.enterprise.modules.project.domain.model.lifecycle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Project-owned lifecycle instance reconstructed by the aggregate repository. */
public final class LifecycleInstance {
    private final Long id;
    private final Long projectId;
    private final Long sourceTemplateId;
    private final Long sourceTemplateVersionId;
    private final int sourceVersionNo;
    private final String sourceChecksum;
    private final String sourceType;
    private final String sourceTemplateCode;
    private final String sourceTemplateName;
    private final String sourceVersionName;
    private final String snapshotChecksum;
    private final String progressPolicy;
    private final String snapshotStatus;
    private final String status;
    private final List<LifecycleStage> stages;

    public LifecycleInstance(
            Long id,
            Long projectId,
            Long sourceTemplateId,
            Long sourceTemplateVersionId,
            int sourceVersionNo,
            String sourceChecksum,
            List<LifecycleStage> stages) {
        this(id, projectId, sourceTemplateId, sourceTemplateVersionId, sourceVersionNo,
                sourceChecksum, sourceTemplateId == null ? "LEGACY" : "TEMPLATE",
                null, null, null, null,
                sourceTemplateId == null ? "LEGACY_EQUAL" : "WEIGHTED",
                "READY", "ACTIVE", stages);
    }

    private LifecycleInstance(
            Long id,
            Long projectId,
            Long sourceTemplateId,
            Long sourceTemplateVersionId,
            int sourceVersionNo,
            String sourceChecksum,
            String sourceType,
            String sourceTemplateCode,
            String sourceTemplateName,
            String sourceVersionName,
            String snapshotChecksum,
            String progressPolicy,
            String snapshotStatus,
            String status,
            List<LifecycleStage> stages) {
        this.id = requirePositive(id, "Lifecycle instance id");
        this.projectId = requirePositive(projectId, "Project id");
        this.sourceTemplateId = sourceTemplateId;
        this.sourceTemplateVersionId = sourceTemplateVersionId;
        if (sourceVersionNo < 0) {
            throw new IllegalArgumentException("Template version number must not be negative");
        }
        this.sourceVersionNo = sourceVersionNo;
        this.sourceChecksum = requireText(sourceChecksum, "Template checksum");
        this.sourceType = requireText(sourceType, "Lifecycle source type");
        this.sourceTemplateCode = sourceTemplateCode;
        this.sourceTemplateName = sourceTemplateName;
        this.sourceVersionName = sourceVersionName;
        this.snapshotChecksum = snapshotChecksum;
        this.progressPolicy = requireText(progressPolicy, "Lifecycle progress policy");
        this.snapshotStatus = requireText(snapshotStatus, "Lifecycle snapshot status");
        this.status = requireText(status, "Lifecycle status");
        this.stages = validateStages(projectId, stages);
    }

    /** Creates a compatibility instance for projects stored before lifecycle V2 is available. */
    public static LifecycleInstance legacy(
            Long instanceId, Long projectId, List<LifecycleStage> stages) {
        return new LifecycleInstance(
                instanceId, projectId, null, null, 0, "LEGACY", "LEGACY",
                null, null, null, null, "LEGACY_EQUAL", "READY", "ACTIVE", stages);
    }

    public static LifecycleInstance fromTemplate(
            Long instanceId,
            Long projectId,
            LifecycleTemplateDefinition template,
            String snapshotChecksum,
            List<LifecycleStage> stages) {
        return new LifecycleInstance(
                instanceId, projectId, template.templateId(), template.versionId(),
                template.versionNo(), template.contentChecksum(), "TEMPLATE",
                template.templateCode(), template.templateName(), template.versionName(),
                requireText(snapshotChecksum, "Snapshot checksum"), "WEIGHTED",
                "READY", "ACTIVE", stages);
    }

    public static LifecycleInstance restore(
            Long instanceId,
            Long projectId,
            Long templateId,
            Long templateVersionId,
            Integer versionNo,
            String templateChecksum,
            String sourceType,
            String templateCode,
            String templateName,
            String versionName,
            String snapshotChecksum,
            String progressPolicy,
            String snapshotStatus,
            String status,
            List<LifecycleStage> stages) {
        boolean legacy = "LEGACY".equals(sourceType);
        return new LifecycleInstance(
                instanceId, projectId, templateId, templateVersionId,
                versionNo == null ? 0 : versionNo,
                legacy ? "LEGACY" : templateChecksum,
                sourceType, templateCode, templateName, versionName, snapshotChecksum,
                progressPolicy, snapshotStatus, status, stages);
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getSourceTemplateId() {
        return sourceTemplateId;
    }

    public Long getSourceTemplateVersionId() {
        return sourceTemplateVersionId;
    }

    public int getSourceVersionNo() {
        return sourceVersionNo;
    }

    public String getSourceChecksum() {
        return sourceChecksum;
    }

    public String getSourceType() { return sourceType; }
    public String getSourceTemplateCode() { return sourceTemplateCode; }
    public String getSourceTemplateName() { return sourceTemplateName; }
    public String getSourceVersionName() { return sourceVersionName; }
    public String getSnapshotChecksum() { return snapshotChecksum; }
    public String getProgressPolicy() { return progressPolicy; }
    public String getSnapshotStatus() { return snapshotStatus; }
    public String getStatus() { return status; }

    public List<LifecycleStage> getStages() {
        return stages;
    }

    private static List<LifecycleStage> validateStages(
            Long projectId, List<LifecycleStage> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("Lifecycle must contain at least one stage");
        }
        List<LifecycleStage> copy = List.copyOf(stages);
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (LifecycleStage stage : copy) {
            if (stage == null || !projectId.equals(stage.getProjectId())) {
                throw new IllegalArgumentException("Lifecycle stage must belong to the project");
            }
            if (!codes.add(stage.getSnapshot().getStageCode())) {
                throw new IllegalArgumentException("Duplicate lifecycle stage code");
            }
            if (!orders.add(stage.getSnapshot().getStageOrder())) {
                throw new IllegalArgumentException("Duplicate lifecycle stage order");
            }
        }
        return copy;
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
