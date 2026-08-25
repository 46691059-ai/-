package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.repository.NodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateAdapter;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateContext;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.runtime.PreparedRoleCandidatePool;
import cn.gov.enterprise.modules.workflow.domain.role.runtime.RoleDirectoryCandidateEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.runtime.RoleDirectoryRuntimeContext;
import cn.gov.enterprise.modules.workflow.domain.role.runtime.RoleDirectoryRuntimeTechnicalGate;
import java.util.List;
import java.util.Objects;

/**
 * Resolves ROLE Directory evidence after Node activation. This framework is not a Spring bean and
 * cannot be reached by the disabled production runtime unless a later gate explicitly wires it.
 */
public final class RoleDirectoryRuntimeBridge {
    private final WorkflowInstanceRepository instances;
    private final WorkflowNodeExecutionRepository executions;
    private final NodeResolverBindingRepository nodeBindings;
    private final ResolverBindingRepository resolverBindings;
    private final RoleDirectoryRuntimeTechnicalGate technicalGate;
    private final RoleDirectoryResolver directoryResolver;
    private final RoleCandidateAdapter candidateAdapter;

    public RoleDirectoryRuntimeBridge(
            WorkflowInstanceRepository instances,
            WorkflowNodeExecutionRepository executions,
            NodeResolverBindingRepository nodeBindings,
            ResolverBindingRepository resolverBindings,
            RoleDirectoryRuntimeTechnicalGate technicalGate,
            RoleDirectoryResolver directoryResolver,
            RoleCandidateAdapter candidateAdapter) {
        this.instances = Objects.requireNonNull(instances);
        this.executions = Objects.requireNonNull(executions);
        this.nodeBindings = Objects.requireNonNull(nodeBindings);
        this.resolverBindings = Objects.requireNonNull(resolverBindings);
        this.technicalGate = Objects.requireNonNull(technicalGate);
        this.directoryResolver = Objects.requireNonNull(directoryResolver);
        this.candidateAdapter = Objects.requireNonNull(candidateAdapter);
    }

    /** External Directory work happens here, before the transactional persistence step. */
    public PreparedRoleCandidatePool prepare(Long instanceId, Long nodeExecutionId) {
        WorkflowInstance instance = instances.findById(instanceId)
                .orElseThrow(() -> blocked("workflow instance does not exist"));
        WorkflowNodeExecution execution = executions.findById(nodeExecutionId)
                .orElseThrow(() -> blocked("node execution does not exist"));
        List<NodeResolverBinding> matching = nodeBindings.findByInstanceId(instanceId)
                .stream().filter(binding -> execution.nodeId().equals(binding.nodeId()))
                .toList();
        if (matching.size() != 1) {
            throw blocked("MULTI_ROLE_BINDING_RUNTIME_NOT_SUPPORTED");
        }
        NodeResolverBinding nodeBinding = matching.getFirst();
        if (nodeBinding.strategyType() != AssignmentStrategy.Type.ROLE
                || nodeBinding.targetType() != AssignmentStrategy.Type.ROLE
                || nodeBinding.resolverMode() != ResolverMode.CANDIDATE_POOL) {
            throw blocked("node is not a frozen ROLE Candidate Pool binding");
        }
        WorkflowResolverBinding resolverBinding = resolverBindings
                .findById(nodeBinding.resolverBindingId())
                .orElseThrow(() -> blocked("frozen resolver binding does not exist"));
        RoleDirectoryRuntimeContext context = RoleDirectoryRuntimeContext.from(
                instance, execution, nodeBinding, resolverBinding);

        technicalGate.requireExecutionEligible();
        var resolverContext = context.toResolverContext(resolverBinding);
        var directory = directoryResolver.resolveDirectory(resolverContext);
        RoleCandidateResult candidates = candidateAdapter.adapt(
                new RoleCandidateContext(instance.id(), execution.id(),
                        context.roleCode(), Long.toString(context.resolvedOrganizationId()),
                        context.effectiveAt(), resolverBinding.asLegacyBinding()),
                directory, directory.revision());
        RoleDirectoryCandidateEvidence evidence =
                RoleDirectoryCandidateEvidence.freeze(context, candidates);
        return new PreparedRoleCandidatePool(instance, execution, nodeBinding,
                resolverBinding, context, candidates, evidence);
    }

    private IllegalStateException blocked(String message) {
        return new IllegalStateException(message);
    }
}
