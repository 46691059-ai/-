package cn.gov.enterprise.modules.workflow.domain.role.runtime;

import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverContext;
import java.time.ZoneId;
import java.util.Objects;

/** Runtime facts derived only from immutable Instance/Node snapshots. */
public record RoleDirectoryRuntimeContext(
        Long enterpriseId, Long definitionId, Long definitionVersionId,
        Long instanceId, Long nodeId, Long nodeExecutionId,
        String roleCode, Long resolvedOrganizationId,
        String resolverCode, String resolverVersion, String resolverContractHash,
        java.time.Instant effectiveAt, String traceId) {

    public static RoleDirectoryRuntimeContext from(
            WorkflowInstance instance, WorkflowNodeExecution execution,
            NodeResolverBinding nodeBinding, WorkflowResolverBinding resolverBinding) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(nodeBinding, "nodeBinding");
        Objects.requireNonNull(resolverBinding, "resolverBinding");
        if (execution.status() != WorkflowNodeExecution.Status.ACTIVE
                || execution.activatedTime() == null) {
            throw new IllegalStateException("ROLE node execution must be ACTIVE");
        }
        if (!instance.id().equals(execution.instanceId())
                || !instance.versionId().equals(execution.versionId())
                || !execution.nodeId().equals(nodeBinding.nodeId())
                || !instance.id().equals(nodeBinding.instanceId())
                || !instance.versionId().equals(nodeBinding.definitionVersionId())
                || !nodeBinding.resolverBindingId().equals(resolverBinding.id())
                || !instance.id().equals(resolverBinding.instanceId())) {
            throw new IllegalStateException("ROLE runtime snapshot ownership mismatch");
        }
        if (nodeBinding.organizationScopeType() != OrganizationScopeType.FIXED_ORG
                || nodeBinding.resolvedOrganizationId() == null
                || nodeBinding.effectiveTimePolicy()
                    != EffectiveTimePolicy.NODE_ACTIVATED_AT) {
            throw new IllegalStateException("only frozen FIXED_ORG/NODE_ACTIVATED_AT is supported");
        }
        if (!resolverBinding.contractHash().equals(
                nodeBinding.resolverContractHashSnapshot())) {
            throw new IllegalStateException("frozen resolver contract hash drift");
        }
        return new RoleDirectoryRuntimeContext(instance.enterpriseId(),
                instance.definitionId(), instance.versionId(), instance.id(),
                execution.nodeId(), execution.id(), nodeBinding.roleCode(),
                nodeBinding.resolvedOrganizationId(),
                resolverBinding.resolverCode().value(),
                resolverBinding.resolverVersion().value(),
                resolverBinding.contractHash().value(),
                execution.activatedTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
                execution.traceId() == null ? "ROLE-BRIDGE-" + execution.id()
                        : execution.traceId());
    }

    public RoleResolverContext toResolverContext(WorkflowResolverBinding binding) {
        return new RoleResolverContext(instanceId, nodeExecutionId,
                binding.asLegacyBinding(), roleCode,
                Long.toString(resolvedOrganizationId), Long.toString(enterpriseId),
                effectiveAt, traceId);
    }
}
