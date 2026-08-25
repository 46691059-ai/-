package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.assignment.*;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.*;

final class WorkflowResolverBindingEntityMapper {
    private WorkflowResolverBindingEntityMapper() { }

    static WorkflowResolverBindingSetEntity toEntity(WorkflowResolverBindingSet value) {
        WorkflowResolverBindingSetEntity entity = new WorkflowResolverBindingSetEntity();
        entity.setId(value.id()); entity.setInstanceId(value.instanceId());
        entity.setDefinitionId(value.definitionId()); entity.setDefinitionVersionId(value.definitionVersionId());
        entity.setManifestVersion(value.manifestVersion()); entity.setManifestHash(value.bindingSetHash());
        entity.setBindingCount(value.bindingCount()); entity.setBindingStatus(value.status().name());
        entity.setFrozenTime(value.frozenTime()); entity.setAuditInfo(value.auditInfo()); entity.setVersion(value.version());
        return entity;
    }

    static WorkflowResolverBindingSet toDomain(WorkflowResolverBindingSetEntity entity) {
        return new WorkflowResolverBindingSet(entity.getId(), entity.getInstanceId(), entity.getDefinitionId(),
                entity.getDefinitionVersionId(), entity.getManifestVersion(), entity.getManifestHash(),
                entity.getBindingCount(), WorkflowResolverBindingSet.Status.valueOf(entity.getBindingStatus()),
                entity.getFrozenTime(), entity.getAuditInfo(), entity.getVersion());
    }

    static WorkflowResolverBindingEntity toEntity(WorkflowResolverBinding value) {
        WorkflowResolverBindingEntity entity = new WorkflowResolverBindingEntity();
        entity.setId(value.id()); entity.setBindingSetId(value.bindingSetId()); entity.setInstanceId(value.instanceId());
        entity.setDefinitionVersionId(value.definitionVersionId());
        entity.setResolverCode(value.resolverCode().value()); entity.setResolverVersion(value.resolverVersion().value());
        entity.setStrategyType(value.strategyType().name()); entity.setResolverMode(value.resolverMode().name());
        entity.setContractHash(value.contractHash().value()); entity.setRuleHash(value.ruleHash());
        entity.setBindingStatus(value.status().name()); entity.setFrozenTime(value.frozenTime());
        entity.setAuditInfo(value.auditInfo()); entity.setVersion(value.version());
        return entity;
    }

    static WorkflowResolverBinding toDomain(WorkflowResolverBindingEntity entity) {
        return new WorkflowResolverBinding(entity.getId(), entity.getBindingSetId(), entity.getInstanceId(),
                entity.getDefinitionVersionId(),
                ResolverCode.of(entity.getResolverCode()), ResolverVersion.of(entity.getResolverVersion()),
                AssignmentStrategy.Type.valueOf(entity.getStrategyType()), ResolverMode.valueOf(entity.getResolverMode()),
                ResolverContractHash.of(entity.getContractHash()), entity.getRuleHash(),
                WorkflowResolverBindingSet.Status.valueOf(entity.getBindingStatus()), entity.getFrozenTime(),
                entity.getAuditInfo(), entity.getVersion());
    }

    static WorkflowNodeResolverBindingEntity toEntity(NodeResolverBinding value) {
        WorkflowNodeResolverBindingEntity entity = new WorkflowNodeResolverBindingEntity();
        entity.setId(value.id()); entity.setBindingSetId(value.bindingSetId());
        entity.setResolverBindingId(value.resolverBindingId()); entity.setInstanceId(value.instanceId());
        entity.setDefinitionVersionId(value.definitionVersionId()); entity.setNodeId(value.nodeId());
        entity.setNodeCodeSnapshot(value.nodeCodeSnapshot());
        entity.setVersionBindingId(value.versionBindingId());
        entity.setVersionBindingOrder(value.versionBindingOrder());
        entity.setVersionBindingHash(value.versionBindingHash());
        entity.setResolverContractHashSnapshot(value.resolverContractHashSnapshot() == null
                ? null : value.resolverContractHashSnapshot().value());
        entity.setRoleCode(value.roleCode());
        entity.setOrganizationScopeType(value.organizationScopeType() == null
                ? null : value.organizationScopeType().name());
        entity.setResolvedOrganizationId(value.resolvedOrganizationId());
        entity.setEffectiveTimePolicy(value.effectiveTimePolicy() == null
                ? null : value.effectiveTimePolicy().name());
        entity.setBindingSchemaVersion(value.bindingSchemaVersion());
        entity.setStrategyType(value.strategyType().name());
        entity.setResolverMode(value.resolverMode().name()); entity.setTargetType(value.targetType().name());
        entity.setTargetValueSnapshot(value.targetValueSnapshot()); entity.setRuleVersion(value.ruleVersion());
        entity.setRuleSnapshot(value.ruleSnapshot()); entity.setRuleHash(value.ruleSnapshotHash());
        entity.setNodeBindingHash(value.nodeBindingHash()); entity.setBindingStatus(value.status().name());
        entity.setFrozenTime(value.frozenTime()); entity.setAuditInfo(value.auditInfo()); entity.setVersion(value.version());
        return entity;
    }

    static NodeResolverBinding toDomain(WorkflowNodeResolverBindingEntity entity) {
        return new NodeResolverBinding(entity.getId(), entity.getBindingSetId(), entity.getResolverBindingId(),
                entity.getInstanceId(), entity.getDefinitionVersionId(), entity.getNodeId(), entity.getNodeCodeSnapshot(),
                AssignmentStrategy.Type.valueOf(entity.getStrategyType()), ResolverMode.valueOf(entity.getResolverMode()),
                AssignmentStrategy.Type.valueOf(entity.getTargetType()), entity.getTargetValueSnapshot(),
                entity.getRuleVersion(), entity.getRuleSnapshot(), entity.getRuleHash(), entity.getNodeBindingHash(),
                entity.getVersionBindingId(), entity.getVersionBindingOrder(), entity.getVersionBindingHash(),
                entity.getResolverContractHashSnapshot() == null ? null
                        : ResolverContractHash.of(entity.getResolverContractHashSnapshot()),
                entity.getRoleCode(), entity.getOrganizationScopeType() == null ? null
                        : cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType.valueOf(
                                entity.getOrganizationScopeType()),
                entity.getResolvedOrganizationId(), entity.getEffectiveTimePolicy() == null ? null
                        : cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy.valueOf(
                                entity.getEffectiveTimePolicy()),
                entity.getBindingSchemaVersion(),
                WorkflowResolverBindingSet.Status.valueOf(entity.getBindingStatus()), entity.getFrozenTime(),
                entity.getAuditInfo(), entity.getVersion());
    }
}
