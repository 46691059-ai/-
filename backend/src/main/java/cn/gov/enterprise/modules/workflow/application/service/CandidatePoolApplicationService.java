package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.FreezeCandidatePoolCommand;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowCandidatePoolDetail;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidateMemberStatus;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolMember;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolMemberRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.NodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Candidate Pool persistence boundary; no Claim or Resolver execution exists here. */
@Service
public class CandidatePoolApplicationService {
    private final CandidatePoolRepository poolRepository;
    private final CandidatePoolMemberRepository memberRepository;
    private final WorkflowTaskRepository taskRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowNodeExecutionRepository executionRepository;
    private final WorkflowTaskAssignmentSnapshotRepository snapshotRepository;
    private final ResolverBindingRepository resolverRepository;
    private final NodeResolverBindingRepository nodeResolverRepository;
    private final WorkflowIdentityGenerator identityGenerator;
    private final CurrentSecurityContext securityContext;

    public CandidatePoolApplicationService(
            CandidatePoolRepository poolRepository,
            CandidatePoolMemberRepository memberRepository,
            WorkflowTaskRepository taskRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowNodeExecutionRepository executionRepository,
            WorkflowTaskAssignmentSnapshotRepository snapshotRepository,
            ResolverBindingRepository resolverRepository,
            NodeResolverBindingRepository nodeResolverRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext) {
        this.poolRepository = poolRepository; this.memberRepository = memberRepository;
        this.taskRepository = taskRepository; this.instanceRepository = instanceRepository;
        this.executionRepository = executionRepository; this.snapshotRepository = snapshotRepository;
        this.resolverRepository = resolverRepository; this.nodeResolverRepository = nodeResolverRepository;
        this.identityGenerator = identityGenerator; this.securityContext = securityContext;
    }

    /** Internal transactional freeze operation. No Controller exposes this method. */
    @Transactional
    public CandidatePool freeze(FreezeCandidatePoolCommand command) {
        if (command == null || command.members() == null || command.members().isEmpty()) {
            throw new BusinessException("B2660", "Candidate Pool command must contain members");
        }
        WorkflowTask task = taskRepository.findById(command.taskId())
                .orElseThrow(() -> missing("workflow task"));
        if (task.assignmentMode() != WorkflowTask.AssignmentMode.CANDIDATE_POOL) {
            throw new BusinessException("B2662", "DIRECT task must not create a Candidate Pool");
        }
        if (poolRepository.existsByTaskId(task.id())) {
            throw new BusinessException("B2669", "Candidate Pool already exists for task");
        }
        WorkflowInstance instance = instanceRepository.findById(task.instanceId())
                .orElseThrow(() -> missing("workflow instance"));
        WorkflowNodeExecution execution = executionRepository.findById(task.nodeExecutionId())
                .orElseThrow(() -> missing("workflow node execution"));
        requireTaskOwnership(task, instance, execution);
        AssignmentSnapshot snapshot = snapshotRepository.findByTaskId(task.id())
                .orElseThrow(() -> missing("workflow assignment snapshot"));
        NodeResolverBinding nodeBinding = nodeResolverRepository
                .findByInstanceIdAndNodeId(task.instanceId(), task.nodeId())
                .orElseThrow(() -> missing("frozen node resolver binding"));
        WorkflowResolverBinding resolver = resolverRepository.findById(nodeBinding.resolverBindingId())
                .orElseThrow(() -> missing("frozen resolver binding"));
        requireFrozenOwnership(task, snapshot, nodeBinding, resolver);

        long poolId = identityGenerator.nextId();
        List<CandidatePoolMember> members = command.members().stream().map(input ->
                new CandidatePoolMember(identityGenerator.nextId(), poolId, task.id(),
                        task.instanceId(), input.candidateUserId(), input.sourceType(),
                        input.sourceRefSnapshot(), input.orgIdSnapshot(),
                        input.positionIdSnapshot(), input.roleIdSnapshot(),
                        input.eligibilitySnapshot(),
                        ResolverContractHash.sha256(input.eligibilitySnapshot()).value(),
                        input.sortOrder(), command.generatedTime(), CandidateMemberStatus.INCLUDED,
                        input.auditInfo(), 0)).toList();
        CandidatePool pool = CandidatePool.created(poolId, "WCP-" + poolId, task.id(),
                task.instanceId(), task.versionId(), task.nodeId(), task.nodeExecutionId(),
                nodeBinding.bindingSetId(), resolver.id(), nodeBinding.id(), snapshot.id(),
                resolver.strategyType(), resolver.resolverCode().value(),
                resolver.resolverVersion().value(), resolver.contractHash(), nodeBinding.ruleSnapshotHash(),
                command.generatedTime(), command.effectiveTime(), command.expiresTime(),
                members, command.auditInfo()).available();
        poolRepository.save(pool);
        memberRepository.saveAll(pool.members());
        return pool;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:view')")
    public WorkflowCandidatePoolDetail query(Long taskId) {
        WorkflowTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> missing("workflow task"));
        WorkflowInstance instance = instanceRepository.findById(task.instanceId())
                .orElseThrow(() -> missing("workflow instance"));
        requireAccessible(instance, securityContext.principal());
        return poolRepository.findByTaskId(taskId).map(pool -> {
            if (task.assignmentMode() != WorkflowTask.AssignmentMode.CANDIDATE_POOL) {
                throw new BusinessException("B2663", "DIRECT task has unexpected Candidate Pool data");
            }
            return WorkflowCandidatePoolDetail.frozen(pool);
        }).orElseGet(() -> {
            if (task.assignmentMode() == WorkflowTask.AssignmentMode.CANDIDATE_POOL) {
                throw new BusinessException("B2664", "Candidate Pool task has no frozen Pool");
            }
            return WorkflowCandidatePoolDetail.direct(task);
        });
    }

    private void requireTaskOwnership(
            WorkflowTask task, WorkflowInstance instance, WorkflowNodeExecution execution) {
        if (!task.instanceId().equals(instance.id()) || !task.versionId().equals(instance.versionId())
                || !task.instanceId().equals(execution.instanceId())
                || !task.versionId().equals(execution.versionId())
                || !task.nodeId().equals(execution.nodeId())
                || !task.nodeExecutionId().equals(execution.id())) {
            throw new BusinessException("B2662", "Candidate Pool task ownership is inconsistent");
        }
    }

    private void requireFrozenOwnership(
            WorkflowTask task, AssignmentSnapshot snapshot,
            NodeResolverBinding node, WorkflowResolverBinding resolver) {
        boolean sameTask = snapshot.taskId().equals(task.id())
                && snapshot.instanceId().equals(task.instanceId())
                && snapshot.versionId().equals(task.versionId())
                && snapshot.nodeId().equals(task.nodeId())
                && snapshot.nodeExecutionId().equals(task.nodeExecutionId());
        boolean sameNode = node.instanceId().equals(task.instanceId())
                && node.definitionVersionId().equals(task.versionId())
                && node.nodeId().equals(task.nodeId())
                && node.resolverBindingId().equals(resolver.id());
        boolean sameResolver = resolver.instanceId().equals(task.instanceId())
                && resolver.definitionVersionId().equals(task.versionId())
                && resolver.bindingSetId().equals(node.bindingSetId())
                && resolver.strategyType() == node.strategyType()
                && resolver.resolverMode() == ResolverMode.CANDIDATE_POOL
                && node.resolverMode() == ResolverMode.CANDIDATE_POOL;
        if (!sameTask || !sameNode || !sameResolver) {
            throw new BusinessException("B2662", "Candidate Pool frozen binding is inconsistent");
        }
    }

    private void requireAccessible(WorkflowInstance instance, SecurityPrincipal principal) {
        boolean accessible = principal.allDataScope()
                || instance.initiatorUserId().equals(principal.userId())
                || (principal.allowedOrgIds() != null
                    && principal.allowedOrgIds().contains(instance.initiatorOrgId()));
        if (!accessible) throw new BusinessException("B2663", "Candidate Pool is outside current data scope");
    }

    private BusinessException missing(String object) {
        return new BusinessException("B2664", object + " does not exist");
    }
}
