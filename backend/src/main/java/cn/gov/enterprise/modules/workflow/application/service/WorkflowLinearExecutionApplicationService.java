package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.ProcessWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowLinearCompletionResult;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTaskAction;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentContext;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolutionResult;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverContext;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResult;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserAssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskActionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.service.ExplicitUserAssignment;
import cn.gov.enterprise.modules.workflow.domain.service.NodeExecutionService;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowLinearExecutor;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Atomic command boundary for MULTI_NODE_LINEAR_V1 task completion and advancement. */
@Service
public class WorkflowLinearExecutionApplicationService {
    private final WorkflowTaskRepository taskRepository;
    private final WorkflowTaskActionRepository actionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowNodeExecutionRepository executionRepository;
    private final WorkflowTransitionRepository transitionRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowIdentityGenerator identityGenerator;
    private final CurrentSecurityContext securityContext;
    private final WorkflowTaskAssignmentSnapshotRepository assignmentSnapshotRepository;
    private final WorkflowAssignmentResolverApplicationService resolverService;
    private final WorkflowResolverBindingApplicationService bindingService;
    private final WorkflowLinearExecutor executor = new WorkflowLinearExecutor();
    private final NodeExecutionService executionService = new NodeExecutionService();
    private final ExplicitUserAssignment assignment = new ExplicitUserAssignment();
    private final ExplicitUserAssignmentStrategy assignmentStrategy = new ExplicitUserAssignmentStrategy();

    @Autowired
    public WorkflowLinearExecutionApplicationService(
            WorkflowTaskRepository taskRepository,
            WorkflowTaskActionRepository actionRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowNodeExecutionRepository executionRepository,
            WorkflowTransitionRepository transitionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext,
            WorkflowTaskAssignmentSnapshotRepository assignmentSnapshotRepository,
            WorkflowAssignmentResolverApplicationService resolverService,
            WorkflowResolverBindingApplicationService bindingService) {
        this.taskRepository = taskRepository;
        this.actionRepository = actionRepository;
        this.instanceRepository = instanceRepository;
        this.executionRepository = executionRepository;
        this.transitionRepository = transitionRepository;
        this.nodeRepository = nodeRepository;
        this.identityGenerator = identityGenerator;
        this.securityContext = securityContext;
        this.assignmentSnapshotRepository = assignmentSnapshotRepository;
        this.resolverService = resolverService;
        this.bindingService = bindingService;
    }

    /** Compatibility constructor retained for WF3.6.4 tests. */
    public WorkflowLinearExecutionApplicationService(
            WorkflowTaskRepository taskRepository,
            WorkflowTaskActionRepository actionRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowNodeExecutionRepository executionRepository,
            WorkflowTransitionRepository transitionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext,
            WorkflowTaskAssignmentSnapshotRepository assignmentSnapshotRepository,
            WorkflowAssignmentResolverApplicationService resolverService) {
        this(taskRepository, actionRepository, instanceRepository, executionRepository,
                transitionRepository, nodeRepository, identityGenerator, securityContext,
                assignmentSnapshotRepository, resolverService, null);
    }

    /** Compatibility constructor retained for WF3.6.1 callers. */
    public WorkflowLinearExecutionApplicationService(
            WorkflowTaskRepository taskRepository,
            WorkflowTaskActionRepository actionRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowNodeExecutionRepository executionRepository,
            WorkflowTransitionRepository transitionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext,
            WorkflowTaskAssignmentSnapshotRepository assignmentSnapshotRepository) {
        this(taskRepository, actionRepository, instanceRepository, executionRepository,
                transitionRepository, nodeRepository, identityGenerator, securityContext,
                assignmentSnapshotRepository,
                new WorkflowAssignmentResolverApplicationService(ResolverRegistry.explicitUserOnly()), null);
    }

    /** Compatibility constructor retained for pre-V2.6.2 tests. */
    public WorkflowLinearExecutionApplicationService(
            WorkflowTaskRepository taskRepository,
            WorkflowTaskActionRepository actionRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowNodeExecutionRepository executionRepository,
            WorkflowTransitionRepository transitionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext) {
        this(taskRepository, actionRepository, instanceRepository, executionRepository,
                transitionRepository, nodeRepository, identityGenerator, securityContext, null,
                new WorkflowAssignmentResolverApplicationService(ResolverRegistry.explicitUserOnly()), null);
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:approve')")
    public WorkflowLinearCompletionResult complete(
            Long taskId, ProcessWorkflowTaskCommand command) {
        Objects.requireNonNull(command, "command");
        SecurityPrincipal principal = securityContext.principal();
        WorkflowTask initialTask = taskRepository.findById(taskId)
                .orElseThrow(() -> notFound("workflow task"));
        String requestHash = requestHash(taskId, command, principal);
        WorkflowTaskAction existing = actionRepository
                .findByTaskIdAndIdempotencyKey(taskId, command.idempotencyKey()).orElse(null);
        if (existing != null) return replay(existing, requestHash, principal);

        WorkflowInstance instance = instanceRepository.findByIdForUpdate(initialTask.instanceId())
                .orElseThrow(() -> notFound("workflow instance"));
        WorkflowTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> notFound("workflow task"));
        validateAggregate(instance, task, principal);
        WorkflowNodeExecution current = executionRepository.findById(task.nodeExecutionId())
                .orElseThrow(() -> notFound("workflow node execution"));
        if (!Objects.equals(instance.currentNodeExecutionId(), current.id())) {
            throw new BusinessException("B2612", "workflow task is not attached to the current execution");
        }

        List<WorkflowTransition> outgoing = transitionRepository.findOutgoing(
                instance.versionId(), current.nodeId(), WorkflowTransition.TriggerType.APPROVE);
        LocalDateTime now = LocalDateTime.now();
        WorkflowLinearExecutor.Completion completion;
        try {
            completion = executor.complete(task, current, principal.userId(), now, outgoing);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw invalid(exception);
        }

        Long actionId = identityGenerator.nextId();
        WorkflowTaskAction action = WorkflowTaskAction.create(
                actionId, "WFA-" + actionId, task,
                WorkflowTaskAction.ActionType.APPROVE, principal.userId(), principal.orgId(),
                command.comment(), now, command.idempotencyKey(), requestHash, MDC.get("traceId"));
        actionRepository.save(action);
        taskRepository.update(completion.task());
        if (!executionRepository.updateState(completion.execution(), current.version())) {
            throw concurrentChange();
        }

        if (completion.transition().isEmpty()) {
            WorkflowInstance completed = instance.completeLinear(now);
            instanceRepository.update(completed);
            return new WorkflowLinearCompletionResult(action.id(), completion.task().status(),
                    completion.execution().status(), completed.status(), null, null, null,
                    completed.eventSequence());
        }

        WorkflowTransition transition = completion.transition().orElseThrow();
        WorkflowNode nextNode = nodeRepository.findById(transition.toNodeId())
                .orElseThrow(() -> notFound("next workflow node"));
        validateNextNode(instance, nextNode);
        int visitNo = executionRepository.findByInstanceId(instance.id()).stream()
                .filter(item -> item.nodeId().equals(nextNode.id()))
                .map(WorkflowNodeExecution::visitNo).max(Comparator.naturalOrder()).orElse(0) + 1;
        Long nextExecutionId = identityGenerator.nextId();
        WorkflowNodeExecution nextExecution;
        WorkflowTask nextTask;
        AssignmentSnapshot assignmentSnapshot;
        try {
            nextExecution = executionService.enter(nextExecutionId, "WFNE-" + nextExecutionId,
                    instance, nextNode, visitNo, current.id(), transition.id(), MDC.get("traceId"), now);
            Long nextTaskId = identityGenerator.nextId();
            AssignmentContext assignmentContext = new AssignmentContext(
                    nextTaskId, instance.id(), instance.versionId(), nextNode.id(),
                    nextExecution.id(), instance.enterpriseId(), instance.initiatorUserId(),
                    instance.initiatorOrgId(), String.valueOf(principal.userId()), now,
                    MDC.get("traceId"));
            cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding nodeBinding =
                    bindingService == null ? null : bindingService.findNode(instance.id(), nextNode.id()).orElse(null);
            if (bindingService != null && nodeBinding == null) {
                // Only instances without a binding set may use the explicit legacy path.
                if (bindingService.querySetInternal(instance.id()).isPresent()) {
                    throw new BusinessException("B2654", "multi-resolver instance has a missing node binding");
                }
            }
            Long targetUserId = nodeBinding == null ? assignment.resolve(nextNode)
                    : nodeBinding.requireExplicitUserId();
            AssignmentResult assignmentResult = assignmentStrategy.resolve(assignmentContext, targetUserId);
            AssignmentResolver resolver = nodeBinding == null
                    ? resolverService.selectForExistingInstance(instance.requireResolverVersionBinding())
                    : bindingService.runtimeResolver(nodeBinding);
            requireCompatibleResolver(resolver, assignmentResult);
            CandidatePool candidatePool = resolver.resolve(
                    new AssignmentResolverContext(assignmentContext, assignmentResult));
            AssignmentResolutionResult resolution = AssignmentResolutionResult.direct(candidatePool);
            assignmentResult = resolution.toAssignmentResult();
            Long assignmentSnapshotId = identityGenerator.nextId();
            assignmentSnapshot = AssignmentSnapshot.create(
                    assignmentSnapshotId, assignmentContext, assignmentResult);
            LocalDateTime dueTime = nextNode.timeoutMinutes() == null
                    ? null : now.plusMinutes(nextNode.timeoutMinutes());
            nextTask = WorkflowTask.pendingLinear(nextTaskId, "WFT-" + nextTaskId,
                    instance, nextNode, nextExecution, resolution.selectedUserId(),
                    candidatePool.targetSnapshot(), dueTime);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw invalid(exception);
        }
        executionRepository.save(nextExecution);
        taskRepository.save(nextTask);
        if (assignmentSnapshotRepository != null) {
            assignmentSnapshotRepository.save(assignmentSnapshot);
        }
        WorkflowInstance advanced = instance.pointTo(nextNode.id(), nextExecution.id());
        instanceRepository.update(advanced);
        return new WorkflowLinearCompletionResult(action.id(), completion.task().status(),
                completion.execution().status(), advanced.status(), nextExecution.id(),
                nextExecution.status(), nextTask.id(), advanced.eventSequence());
    }

    private WorkflowLinearCompletionResult replay(
            WorkflowTaskAction action, String requestHash, SecurityPrincipal principal) {
        if (action.actionType() != WorkflowTaskAction.ActionType.APPROVE
                || !action.requestHash().equals(requestHash)
                || !action.operatorUserId().equals(principal.userId())) {
            throw new BusinessException("B2619", "idempotency key was used by another task action");
        }
        WorkflowTask task = taskRepository.findById(action.taskId())
                .orElseThrow(() -> notFound("workflow task"));
        WorkflowNodeExecution completed = executionRepository.findById(task.nodeExecutionId())
                .orElseThrow(() -> notFound("workflow node execution"));
        WorkflowInstance instance = instanceRepository.findById(task.instanceId())
                .orElseThrow(() -> notFound("workflow instance"));
        WorkflowNodeExecution next = instance.currentNodeExecutionId() == null ? null
                : executionRepository.findById(instance.currentNodeExecutionId()).orElse(null);
        WorkflowTask nextTask = next == null ? null
                : taskRepository.findByNodeExecutionId(next.id()).orElse(null);
        return new WorkflowLinearCompletionResult(action.id(), task.status(), completed.status(),
                instance.status(), next == null ? null : next.id(),
                next == null ? null : next.status(), nextTask == null ? null : nextTask.id(),
                instance.eventSequence());
    }

    private void validateAggregate(
            WorkflowInstance instance, WorkflowTask task, SecurityPrincipal principal) {
        if (instance.engineMode() != WorkflowEngineMode.MULTI_NODE_LINEAR_V1
                || instance.status() != WorkflowInstance.Status.RUNNING) {
            throw new BusinessException("B2612", "workflow instance is not a running linear instance");
        }
        if (!instance.id().equals(task.instanceId()) || !instance.versionId().equals(task.versionId())
                || task.nodeExecutionId() == null) {
            throw new BusinessException("B2610", "workflow task runtime ownership is invalid");
        }
        requireAccessible(instance, principal);
        if (task.assigneeUserId() == null || !task.assigneeUserId().equals(principal.userId())) {
            throw new BusinessException("B2613", "workflow task is assigned to another user");
        }
    }

    private void validateNextNode(WorkflowInstance instance, WorkflowNode nextNode) {
        if (!instance.versionId().equals(nextNode.versionId()) || !nextNode.enabled()
                || nextNode.nodeType() != WorkflowNode.NodeType.APPROVAL
                || nextNode.approvalMode() != WorkflowNode.ApprovalMode.SINGLE) {
            throw new BusinessException("B2610", "next workflow node is invalid for linear runtime");
        }
    }

    private void requireCompatibleResolver(
            AssignmentResolver resolver, AssignmentResult assignmentResult) {
        if (resolver.descriptor().strategyType() != assignmentResult.strategyType()
                || resolver.descriptor().mode() != ResolverMode.DIRECT) {
            throw new BusinessException("B2630",
                    "frozen resolver does not support the task assignment strategy or mode");
        }
    }

    private void requireAccessible(WorkflowInstance instance, SecurityPrincipal principal) {
        boolean accessible = principal.allDataScope()
                || instance.initiatorUserId().equals(principal.userId())
                || (principal.allowedOrgIds() != null
                    && principal.allowedOrgIds().contains(instance.initiatorOrgId()));
        if (!accessible) throw new BusinessException("B2613", "workflow task is outside current data scope");
    }

    private String requestHash(
            Long taskId, ProcessWorkflowTaskCommand command, SecurityPrincipal principal) {
        String canonical = String.join("\u001f", Objects.toString(taskId, ""), "COMPLETE",
                Objects.toString(command.comment(), "").trim(), command.idempotencyKey().trim(),
                Objects.toString(principal.userId(), ""), Objects.toString(principal.orgId(), ""));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private BusinessException notFound(String resource) {
        return new BusinessException("B2614", resource + " does not exist");
    }

    private BusinessException invalid(RuntimeException cause) {
        BusinessException exception = new BusinessException("B2610", cause.getMessage());
        exception.initCause(cause);
        return exception;
    }

    private BusinessException concurrentChange() {
        return new BusinessException("B2619", "workflow task was completed concurrently");
    }
}
