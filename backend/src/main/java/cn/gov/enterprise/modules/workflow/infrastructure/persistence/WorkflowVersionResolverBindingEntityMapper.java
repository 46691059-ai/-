package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifest;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionNodeResolverBindingEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionResolverBindingManifestEntity;

final class WorkflowVersionResolverBindingEntityMapper {
    private WorkflowVersionResolverBindingEntityMapper() {}

    static VersionNodeResolverBinding toDomain(WorkflowVersionNodeResolverBindingEntity entity) {
        try {
            return new VersionNodeResolverBinding(
                    entity.getId(), entity.getDefinitionId(), entity.getDefinitionVersionId(),
                    entity.getNodeId(), entity.getBindingOrder(), ResolverCode.of(entity.getResolverCode()),
                    ResolverVersion.of(entity.getResolverVersion()),
                    ResolverContractHash.of(entity.getResolverContractHash()),
                    AssignmentStrategy.Type.valueOf(entity.getStrategyType()),
                    ResolverMode.valueOf(entity.getResolverMode()),
                    AssignmentStrategy.Type.valueOf(entity.getTargetType()), entity.getRoleCode(),
                    OrganizationScopeType.valueOf(entity.getOrganizationScopeType()),
                    entity.getOrganizationId(),
                    EffectiveTimePolicy.valueOf(entity.getEffectiveTimePolicy()),
                    entity.getBindingSchemaVersion(), entity.getBindingHash(), entity.getVersion());
        } catch (RuntimeException exception) {
            throw corrupt("workflow_version_node_resolver_binding", exception);
        }
    }

    static WorkflowVersionNodeResolverBindingEntity toEntity(VersionNodeResolverBinding value) {
        WorkflowVersionNodeResolverBindingEntity entity = new WorkflowVersionNodeResolverBindingEntity();
        entity.setId(value.id());
        entity.setDefinitionId(value.definitionId());
        entity.setDefinitionVersionId(value.definitionVersionId());
        entity.setNodeId(value.nodeId());
        entity.setBindingOrder(value.bindingOrder());
        entity.setResolverCode(value.resolverCode().value());
        entity.setResolverVersion(value.resolverVersion().value());
        entity.setResolverContractHash(value.resolverContractHash().value());
        entity.setStrategyType(value.strategyType().name());
        entity.setResolverMode(value.resolverMode().name());
        entity.setTargetType(value.targetType().name());
        entity.setRoleCode(value.roleCode());
        entity.setOrganizationScopeType(value.organizationScopeType().name());
        entity.setOrganizationId(value.organizationId());
        entity.setEffectiveTimePolicy(value.effectiveTimePolicy().name());
        entity.setBindingSchemaVersion(value.bindingSchemaVersion());
        entity.setBindingHash(value.bindingHash());
        entity.setVersion(value.version());
        return entity;
    }

    static ResolverBindingManifest toDomain(WorkflowVersionResolverBindingManifestEntity entity) {
        try {
            return new ResolverBindingManifest(
                    entity.getId(), entity.getDefinitionId(), entity.getDefinitionVersionId(),
                    entity.getCanonicalVersion(), entity.getBindingCount(), entity.getManifestHash(),
                    entity.getReleasedBy(), entity.getReleasedTime());
        } catch (RuntimeException exception) {
            throw corrupt("workflow_version_resolver_binding_manifest", exception);
        }
    }

    static WorkflowVersionResolverBindingManifestEntity toEntity(ResolverBindingManifest value) {
        WorkflowVersionResolverBindingManifestEntity entity =
                new WorkflowVersionResolverBindingManifestEntity();
        entity.setId(value.id());
        entity.setDefinitionId(value.definitionId());
        entity.setDefinitionVersionId(value.definitionVersionId());
        entity.setCanonicalVersion(value.canonicalVersion());
        entity.setBindingCount(value.bindingCount());
        entity.setManifestHash(value.manifestHash());
        entity.setReleasedBy(value.releasedBy());
        entity.setReleasedTime(value.releasedTime());
        return entity;
    }

    private static BusinessException corrupt(String table, RuntimeException cause) {
        BusinessException exception = new BusinessException(
                "B2621", table + " data violates Version resolver binding rules");
        exception.initCause(cause);
        return exception;
    }
}
