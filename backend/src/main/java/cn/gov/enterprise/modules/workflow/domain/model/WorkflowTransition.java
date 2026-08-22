package cn.gov.enterprise.modules.workflow.domain.model;

import java.util.Objects;

/** Immutable directed edge owned by a frozen workflow version. */
public record WorkflowTransition(
        Long id, Long versionId, String transitionCode, String transitionName,
        Long fromNodeId, Long toNodeId, TriggerType triggerType, RouteType routeType,
        int priority, String conditionConfig, boolean enabled, int version) {

    public enum TriggerType { APPROVE, REJECT }
    public enum RouteType { DIRECT, CONDITIONAL_RESERVED }

    public WorkflowTransition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(versionId, "versionId");
        transitionCode = required(transitionCode, "transitionCode", 100);
        transitionName = required(transitionName, "transitionName", 200);
        Objects.requireNonNull(fromNodeId, "fromNodeId");
        Objects.requireNonNull(toNodeId, "toNodeId");
        if (fromNodeId.equals(toNodeId)) throw new IllegalArgumentException("transition cannot target its source node");
        Objects.requireNonNull(triggerType, "triggerType");
        Objects.requireNonNull(routeType, "routeType");
        if (priority <= 0) throw new IllegalArgumentException("priority must be positive");
        if (routeType == RouteType.DIRECT && conditionConfig != null) {
            throw new IllegalArgumentException("DIRECT transition cannot contain conditionConfig");
        }
        if (routeType == RouteType.CONDITIONAL_RESERVED
                && (conditionConfig == null || conditionConfig.isBlank())) {
            throw new IllegalArgumentException("CONDITIONAL_RESERVED transition requires conditionConfig");
        }
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
