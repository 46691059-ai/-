package cn.gov.enterprise.modules.project.domain.model.lifecycle;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Active, validated and immutable lifecycle template version selected for project creation. */
public record LifecycleTemplateDefinition(
        Long templateId,
        Long versionId,
        String templateCode,
        String templateName,
        int versionNo,
        String versionName,
        String contentChecksum,
        List<LifecycleTemplateStageDefinition> stages) {

    public LifecycleTemplateDefinition {
        if (templateId == null || templateId <= 0 || versionId == null || versionId <= 0) {
            throw new IllegalArgumentException("Template and version ids must be positive");
        }
        templateCode = requireText(templateCode, "Template code");
        templateName = requireText(templateName, "Template name");
        if (versionNo <= 0) throw new IllegalArgumentException("Template version must be positive");
        contentChecksum = requireText(contentChecksum, "Template checksum");
        stages = validateStages(stages);
    }

    private static List<LifecycleTemplateStageDefinition> validateStages(
            List<LifecycleTemplateStageDefinition> source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("Lifecycle template must contain stages");
        }
        List<LifecycleTemplateStageDefinition> sorted = source.stream()
                .sorted(Comparator.comparingInt(LifecycleTemplateStageDefinition::stageOrder))
                .toList();
        Set<String> codes = new HashSet<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        int autoStartCount = 0;
        for (int index = 0; index < sorted.size(); index++) {
            LifecycleTemplateStageDefinition stage = sorted.get(index);
            if (stage.stageOrder() != index + 1 || !codes.add(stage.stageCode())) {
                throw new IllegalArgumentException("Lifecycle stage order or code is invalid");
            }
            totalWeight = totalWeight.add(stage.progressWeight());
            if (stage.autoStart()) autoStartCount++;
        }
        if (totalWeight.compareTo(new BigDecimal("100.0000")) != 0) {
            throw new IllegalArgumentException("Lifecycle stage weights must total 100");
        }
        if (autoStartCount != 1 || !sorted.getFirst().autoStart()) {
            throw new IllegalArgumentException("Exactly the first lifecycle stage must auto-start");
        }
        return sorted;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
