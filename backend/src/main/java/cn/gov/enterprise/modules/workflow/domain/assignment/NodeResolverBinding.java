package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable node assignment rule and exact resolver reference. */
public record NodeResolverBinding(
        Long id, Long bindingSetId, Long resolverBindingId, Long instanceId,
        Long definitionVersionId, Long nodeId, String nodeCodeSnapshot,
        AssignmentStrategy.Type strategyType, ResolverMode resolverMode,
        AssignmentStrategy.Type targetType, String targetValueSnapshot,
        String ruleVersion, String ruleSnapshot, String ruleSnapshotHash,
        String nodeBindingHash, WorkflowResolverBindingSet.Status status,
        LocalDateTime frozenTime, String auditInfo, int version) {
    private static final Pattern USER_ID = Pattern.compile("\\\"userId\\\"\\s*:\\s*(\\d+)");

    public NodeResolverBinding {
        positive(id, "id"); positive(bindingSetId, "bindingSetId");
        positive(resolverBindingId, "resolverBindingId"); positive(instanceId, "instanceId");
        positive(definitionVersionId, "definitionVersionId"); positive(nodeId, "nodeId");
        nodeCodeSnapshot = WorkflowResolverBindingSet.required(nodeCodeSnapshot, "nodeCodeSnapshot", 100);
        Objects.requireNonNull(strategyType, "strategyType");
        Objects.requireNonNull(resolverMode, "resolverMode");
        Objects.requireNonNull(targetType, "targetType");
        if (strategyType != targetType) throw new IllegalArgumentException("strategyType and targetType must match");
        targetValueSnapshot = WorkflowResolverBindingSet.required(targetValueSnapshot, "targetValueSnapshot", 65535);
        ruleVersion = WorkflowResolverBindingSet.required(ruleVersion, "ruleVersion", 32);
        ruleSnapshot = WorkflowResolverBindingSet.required(ruleSnapshot, "ruleSnapshot", 65535);
        WorkflowResolverBindingSet.sha256(ruleSnapshotHash, "ruleSnapshotHash");
        WorkflowResolverBindingSet.sha256(nodeBindingHash, "nodeBindingHash");
        Objects.requireNonNull(status, "status"); Objects.requireNonNull(frozenTime, "frozenTime");
        auditInfo = WorkflowResolverBindingSet.required(auditInfo, "auditInfo", 65535);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    public Long requireExplicitUserId() {
        if (strategyType != AssignmentStrategy.Type.USER || resolverMode != ResolverMode.DIRECT) {
            throw new IllegalStateException("node binding is not USER + DIRECT");
        }
        Matcher matcher = USER_ID.matcher(targetValueSnapshot);
        if (!matcher.find()) throw new IllegalStateException("frozen node binding has no userId");
        long userId = Long.parseLong(matcher.group(1));
        if (userId <= 0) throw new IllegalStateException("frozen userId must be positive");
        return userId;
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
