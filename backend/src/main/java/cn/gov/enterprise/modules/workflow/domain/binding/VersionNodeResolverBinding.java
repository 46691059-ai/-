package cn.gov.enterprise.modules.workflow.domain.binding;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.util.Objects;
import java.util.regex.Pattern;

/** DRAFT-owned resolver configuration for one node in one Workflow Version. */
public record VersionNodeResolverBinding(
        Long id,
        Long definitionId,
        Long definitionVersionId,
        Long nodeId,
        int bindingOrder,
        ResolverCode resolverCode,
        ResolverVersion resolverVersion,
        ResolverContractHash resolverContractHash,
        AssignmentStrategy.Type strategyType,
        ResolverMode resolverMode,
        AssignmentStrategy.Type targetType,
        String roleCode,
        OrganizationScopeType organizationScopeType,
        Long organizationId,
        EffectiveTimePolicy effectiveTimePolicy,
        String bindingSchemaVersion,
        String bindingHash,
        int version) {
    public static final String ROLE_RESOLVER_CODE = "ROLE_DIRECTORY";
    public static final String ROLE_RESOLVER_VERSION = "ROLE_DIRECTORY_V1";
    public static final String SCHEMA_VERSION = "VERSION_NODE_RESOLVER_BINDING_V1";
    private static final Pattern ROLE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,99}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public VersionNodeResolverBinding {
        positive(id, "id");
        positive(definitionId, "definitionId");
        positive(definitionVersionId, "definitionVersionId");
        positive(nodeId, "nodeId");
        if (bindingOrder < 1) throw new IllegalArgumentException("bindingOrder must be positive");
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(resolverContractHash, "resolverContractHash");
        Objects.requireNonNull(strategyType, "strategyType");
        Objects.requireNonNull(resolverMode, "resolverMode");
        Objects.requireNonNull(targetType, "targetType");
        roleCode = stableRoleCode(roleCode);
        Objects.requireNonNull(organizationScopeType, "organizationScopeType");
        validateOrganizationScope(organizationScopeType, organizationId);
        Objects.requireNonNull(effectiveTimePolicy, "effectiveTimePolicy");
        bindingSchemaVersion = required(bindingSchemaVersion, "bindingSchemaVersion", 64);
        bindingHash = lowercaseSha256(bindingHash, "bindingHash");
        if (!ROLE_RESOLVER_CODE.equals(resolverCode.value())
                || !ROLE_RESOLVER_VERSION.equals(resolverVersion.value())
                || strategyType != AssignmentStrategy.Type.ROLE
                || resolverMode != ResolverMode.CANDIDATE_POOL
                || targetType != AssignmentStrategy.Type.ROLE) {
            throw new IllegalArgumentException("unsupported V2.6.21 resolver binding combination");
        }
        if (effectiveTimePolicy != EffectiveTimePolicy.NODE_ACTIVATED_AT) {
            throw new IllegalArgumentException("unsupported effectiveTimePolicy");
        }
        if (!SCHEMA_VERSION.equals(bindingSchemaVersion)) {
            throw new IllegalArgumentException("unsupported bindingSchemaVersion");
        }
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    public VersionNodeResolverBinding withBindingHash(String computedHash) {
        return new VersionNodeResolverBinding(
                id, definitionId, definitionVersionId, nodeId, bindingOrder,
                resolverCode, resolverVersion, resolverContractHash, strategyType,
                resolverMode, targetType, roleCode, organizationScopeType, organizationId,
                effectiveTimePolicy, bindingSchemaVersion, computedHash, version);
    }

    private static void validateOrganizationScope(OrganizationScopeType scope, Long organizationId) {
        if (scope == OrganizationScopeType.FIXED_ORG
                && (organizationId == null || organizationId <= 0)) {
            throw new IllegalArgumentException("FIXED_ORG requires a positive organizationId");
        }
        if (scope == OrganizationScopeType.INSTANCE_BUSINESS_ORG && organizationId != null) {
            throw new IllegalArgumentException("INSTANCE_BUSINESS_ORG must not freeze organizationId");
        }
    }

    private static String stableRoleCode(String value) {
        String normalized = required(value, "roleCode", 100);
        if (!ROLE_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("roleCode must be an uppercase stable key");
        }
        return normalized;
    }

    static String lowercaseSha256(String value, String field) {
        String normalized = required(value, field, 64);
        if (!SHA_256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
