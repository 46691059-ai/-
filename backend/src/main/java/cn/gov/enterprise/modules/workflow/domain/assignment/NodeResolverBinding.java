package cn.gov.enterprise.modules.workflow.domain.assignment;

import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
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
        String nodeBindingHash, Long versionBindingId, Integer versionBindingOrder,
        String versionBindingHash, ResolverContractHash resolverContractHashSnapshot,
        String roleCode, OrganizationScopeType organizationScopeType,
        Long resolvedOrganizationId, EffectiveTimePolicy effectiveTimePolicy,
        String bindingSchemaVersion, WorkflowResolverBindingSet.Status status,
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
        validateSource(strategyType, resolverMode, targetType, versionBindingId,
                versionBindingOrder, versionBindingHash, resolverContractHashSnapshot,
                roleCode, organizationScopeType, resolvedOrganizationId,
                effectiveTimePolicy, bindingSchemaVersion);
        Objects.requireNonNull(status, "status"); Objects.requireNonNull(frozenTime, "frozenTime");
        auditInfo = WorkflowResolverBindingSet.required(auditInfo, "auditInfo", 65535);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    /** Compatibility constructor for immutable Legacy USER + DIRECT snapshots. */
    public NodeResolverBinding(
            Long id, Long bindingSetId, Long resolverBindingId, Long instanceId,
            Long definitionVersionId, Long nodeId, String nodeCodeSnapshot,
            AssignmentStrategy.Type strategyType, ResolverMode resolverMode,
            AssignmentStrategy.Type targetType, String targetValueSnapshot,
            String ruleVersion, String ruleSnapshot, String ruleSnapshotHash,
            String nodeBindingHash, WorkflowResolverBindingSet.Status status,
            LocalDateTime frozenTime, String auditInfo, int version) {
        this(id, bindingSetId, resolverBindingId, instanceId, definitionVersionId,
                nodeId, nodeCodeSnapshot, strategyType, resolverMode, targetType,
                targetValueSnapshot, ruleVersion, ruleSnapshot, ruleSnapshotHash,
                nodeBindingHash, null, null, null, null, null, null, null, null,
                null, status, frozenTime, auditInfo, version);
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

    private static void validateSource(
            AssignmentStrategy.Type strategyType, ResolverMode resolverMode,
            AssignmentStrategy.Type targetType, Long versionBindingId,
            Integer versionBindingOrder, String versionBindingHash,
            ResolverContractHash resolverContractHashSnapshot, String roleCode,
            OrganizationScopeType organizationScopeType, Long resolvedOrganizationId,
            EffectiveTimePolicy effectiveTimePolicy, String bindingSchemaVersion) {
        if (strategyType == AssignmentStrategy.Type.USER
                && resolverMode == ResolverMode.DIRECT
                && targetType == AssignmentStrategy.Type.USER) {
            if (versionBindingId != null || versionBindingOrder != null
                    || versionBindingHash != null || resolverContractHashSnapshot != null
                    || roleCode != null || organizationScopeType != null
                    || resolvedOrganizationId != null || effectiveTimePolicy != null
                    || bindingSchemaVersion != null) {
                throw new IllegalArgumentException("Legacy USER snapshot must not carry Version ROLE evidence");
            }
            return;
        }
        if (strategyType != AssignmentStrategy.Type.ROLE
                || resolverMode != ResolverMode.CANDIDATE_POOL
                || targetType != AssignmentStrategy.Type.ROLE) {
            throw new IllegalArgumentException("unsupported node resolver binding combination");
        }
        boolean historicalRoleSnapshot = versionBindingId == null
                && versionBindingOrder == null && versionBindingHash == null
                && resolverContractHashSnapshot == null && roleCode == null
                && organizationScopeType == null && resolvedOrganizationId == null
                && effectiveTimePolicy == null && bindingSchemaVersion == null;
        if (historicalRoleSnapshot) {
            return;
        }
        positive(versionBindingId, "versionBindingId");
        if (versionBindingOrder == null || versionBindingOrder < 1) {
            throw new IllegalArgumentException("versionBindingOrder must be positive");
        }
        WorkflowResolverBindingSet.sha256(versionBindingHash, "versionBindingHash");
        Objects.requireNonNull(resolverContractHashSnapshot, "resolverContractHashSnapshot");
        if (roleCode == null || !roleCode.matches("[A-Z][A-Z0-9_]{2,99}")) {
            throw new IllegalArgumentException("roleCode must be a stable uppercase key");
        }
        if (organizationScopeType != OrganizationScopeType.FIXED_ORG
                || resolvedOrganizationId == null || resolvedOrganizationId <= 0) {
            throw new IllegalArgumentException("only FIXED_ORG is supported during Instance freeze");
        }
        if (effectiveTimePolicy != EffectiveTimePolicy.NODE_ACTIVATED_AT) {
            throw new IllegalArgumentException("unsupported effectiveTimePolicy");
        }
        if (!"VERSION_NODE_RESOLVER_BINDING_V1".equals(bindingSchemaVersion)) {
            throw new IllegalArgumentException("unsupported bindingSchemaVersion");
        }
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
