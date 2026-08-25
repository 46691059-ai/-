package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.StartWorkflowCommand;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowInstanceDetail;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowResolverBindingDetail;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentContext;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolutionResult;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverContext;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResult;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserAssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.service.ExplicitUserAssignment;
import cn.gov.enterprise.modules.workflow.domain.service.NodeExecutionService;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowLinearGraphValidator;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowVersionContentHasher;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowRuntimeApplicationService {
    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowVersionRepository versionRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowTaskRepository taskRepository;
    private final WorkflowIdentityGenerator identityGenerator;
    private final CurrentSecurityContext securityContext;
    private final WorkflowNodeExecutionRepository executionRepository;
    private final WorkflowTransitionRepository transitionRepository;
    private final WorkflowTaskAssignmentSnapshotRepository assignmentSnapshotRepository;
    private final WorkflowAssignmentResolverApplicationService resolverService;
    private final WorkflowResolverBindingApplicationService bindingService;
    private final VersionResolverBindingInstanceFreezer versionBindingFreezer;
    private final NodeExecutionService executionService = new NodeExecutionService();
    private final ExplicitUserAssignment assignment = new ExplicitUserAssignment();
    private final ExplicitUserAssignmentStrategy assignmentStrategy = new ExplicitUserAssignmentStrategy();
    private final WorkflowLinearGraphValidator graphValidator = new WorkflowLinearGraphValidator();
    private final WorkflowVersionContentHasher contentHasher = new WorkflowVersionContentHasher();

    @Autowired
    public WorkflowRuntimeApplicationService(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowVersionRepository versionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowTaskRepository taskRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext,
            WorkflowNodeExecutionRepository executionRepository,
            WorkflowTransitionRepository transitionRepository,
            WorkflowTaskAssignmentSnapshotRepository assignmentSnapshotRepository,
            WorkflowAssignmentResolverApplicationService resolverService,
            WorkflowResolverBindingApplicationService bindingService,
            VersionResolverBindingInstanceFreezer versionBindingFreezer) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.nodeRepository = nodeRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.identityGenerator = identityGenerator;
        this.securityContext = securityContext;
        this.executionRepository = executionRepository;
        this.transitionRepository = transitionRepository;
        this.assignmentSnapshotRepository = assignmentSnapshotRepository;
        this.resolverService = resolverService;
        this.bindingService = bindingService;
        this.versionBindingFreezer = versionBindingFreezer;
    }

    /** Compatibility constructor retained for WF3.6.4 tests. */
    public WorkflowRuntimeApplicationService(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowVersionRepository versionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowTaskRepository taskRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext,
            WorkflowNodeExecutionRepository executionRepository,
            WorkflowTransitionRepository transitionRepository,
            WorkflowTaskAssignmentSnapshotRepository assignmentSnapshotRepository,
            WorkflowAssignmentResolverApplicationService resolverService) {
        this(definitionRepository, versionRepository, nodeRepository, instanceRepository,
                taskRepository, identityGenerator, securityContext, executionRepository,
                transitionRepository, assignmentSnapshotRepository, resolverService, null, null);
    }

    /** Compatibility constructor retained for WF3.6.1 callers. */
    public WorkflowRuntimeApplicationService(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowVersionRepository versionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowTaskRepository taskRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext,
            WorkflowNodeExecutionRepository executionRepository,
            WorkflowTransitionRepository transitionRepository,
            WorkflowTaskAssignmentSnapshotRepository assignmentSnapshotRepository) {
        this(definitionRepository, versionRepository, nodeRepository, instanceRepository,
                taskRepository, identityGenerator, securityContext, executionRepository,
                transitionRepository, assignmentSnapshotRepository,
                new WorkflowAssignmentResolverApplicationService(ResolverRegistry.explicitUserOnly()), null, null);
    }

    /** Compatibility constructor retained for pre-V2.6.2 tests. */
    public WorkflowRuntimeApplicationService(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowVersionRepository versionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowTaskRepository taskRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext,
            WorkflowNodeExecutionRepository executionRepository,
            WorkflowTransitionRepository transitionRepository) {
        this(definitionRepository, versionRepository, nodeRepository, instanceRepository,
                taskRepository, identityGenerator, securityContext, executionRepository,
                transitionRepository, null,
                new WorkflowAssignmentResolverApplicationService(ResolverRegistry.explicitUserOnly()), null, null);
    }

    /** Compatibility constructor for legacy runtime tests. */
    public WorkflowRuntimeApplicationService(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowVersionRepository versionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowTaskRepository taskRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext) {
        this(definitionRepository, versionRepository, nodeRepository, instanceRepository,
                taskRepository, identityGenerator, securityContext, null, null, null,
                new WorkflowAssignmentResolverApplicationService(ResolverRegistry.explicitUserOnly()), null, null);
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:start')")
    public WorkflowInstance startWorkflow(StartWorkflowCommand command) {
        SecurityPrincipal principal = securityContext.principal();
        String requestHash = requestHash(command, principal);
        WorkflowInstance existing = instanceRepository
                .findByEnterpriseIdAndIdempotencyKey(command.enterpriseId(), command.idempotencyKey())
                .orElse(null);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessException("B2519", "idempotency key was used by a different request");
            }
            requireAccessible(existing, principal);
            return existing;
        }

        WorkflowDefinition definition = definitionRepository.findById(command.definitionId())
                .orElseThrow(() -> notFound("workflow definition"));
        validateDefinition(definition, command);
        requireStartScope(definition, principal);
        WorkflowVersion workflowVersion = versionRepository.findById(definition.currentVersionId())
                .orElseThrow(() -> notFound("published workflow version"));
        if (workflowVersion.status() != WorkflowVersion.Status.PUBLISHED
                || !definition.id().equals(workflowVersion.definitionId())) {
            throw new BusinessException("B2510", "workflow current version is not published or does not belong to definition");
        }
        LocalDateTime now = LocalDateTime.now();
        if (workflowVersion.contentHash() == null || workflowVersion.effectiveFrom() == null
                || workflowVersion.effectiveFrom().isAfter(now)
                || (workflowVersion.effectiveTo() != null && !now.isBefore(workflowVersion.effectiveTo()))) {
            throw new BusinessException("B2510", "workflow current version is outside its effective publication window");
        }
        List<WorkflowNode> versionNodes = nodeRepository.findByVersionId(workflowVersion.id());
        if (workflowVersion.engineMode() == WorkflowEngineMode.MULTI_NODE_LINEAR_V1) {
            return startLinear(command, principal, definition, workflowVersion, versionNodes,
                    requestHash, now);
        }
        WorkflowNode firstNode = versionNodes.stream()
                .filter(WorkflowNode::enabled)
                .findFirst().orElseThrow(() -> new BusinessException("B2510", "published workflow has no enabled node"));
        if (firstNode.nodeType() != WorkflowNode.NodeType.APPROVAL) {
            throw new BusinessException("B2510", "Workflow Lite can only start with a single approval node");
        }

        Long instanceId = identityGenerator.nextId();
        WorkflowInstance instance;
        WorkflowTask task;
        try {
            instance = WorkflowInstance.running(instanceId, "WFI-" + instanceId, definition, workflowVersion,
                    command.businessType(), command.businessId(), command.businessKey(), command.enterpriseId(),
                    command.snapshotRef(), command.snapshotHash(), command.attemptNo(), principal.userId(),
                    principal.orgId(), firstNode.id(), command.variablesSnapshot(), command.idempotencyKey(),
                    requestHash, MDC.get("traceId"), now);
            Long taskId = identityGenerator.nextId();
            LocalDateTime dueTime = firstNode.timeoutMinutes() == null
                    ? null : now.plusMinutes(firstNode.timeoutMinutes());
            task = WorkflowTask.pending(taskId, "WFT-" + taskId, instance, firstNode,
                    firstNode.nodeCode() + ":1", firstNode.assignmentRuleConfig(), dueTime);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid(exception);
        }
        instanceRepository.save(instance);
        taskRepository.save(task);
        return instance;
    }

    private WorkflowInstance startLinear(
            StartWorkflowCommand command, SecurityPrincipal principal,
            WorkflowDefinition definition, WorkflowVersion workflowVersion,
            List<WorkflowNode> nodes, String requestHash, LocalDateTime now) {
        if (executionRepository == null || transitionRepository == null) {
            throw new BusinessException("B2610", "linear workflow runtime is unavailable");
        }
        List<WorkflowTransition> transitions = transitionRepository.findByVersionId(workflowVersion.id());
        WorkflowNode entry;
        String graphHash;
        try {
            entry = graphValidator.validateAndFindEntry(workflowVersion.id(), nodes, transitions);
            graphHash = contentHasher.hashGraph(definition, workflowVersion, nodes, transitions);
            if (workflowVersion.resolverBindingModel() == ResolverBindingModel.LEGACY_USER_ONLY
                    && !graphHash.equals(workflowVersion.contentHash())) {
                throw new IllegalStateException("published workflow graph hash does not match runtime graph");
            }
        } catch (IllegalStateException exception) {
            throw invalid(exception);
        }
        Long instanceId = identityGenerator.nextId();
        if (workflowVersion.resolverBindingModel()
                == ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE) {
            return freezeRoleInstance(command, principal, definition, workflowVersion,
                    nodes, entry, graphHash, requestHash, now, instanceId);
        }
        ResolverVersionBinding resolverBinding = resolverService.freeze(instanceId,
                ExplicitUserResolver.CODE, ExplicitUserResolver.RESOLVER_VERSION,
                ExplicitUserResolver.CONTRACT_HASH);
        WorkflowInstance instance;
        WorkflowNodeExecution execution;
        WorkflowTask task;
        AssignmentSnapshot assignmentSnapshot;
        WorkflowResolverBindingApplicationService.FrozenBindings frozenBindings = null;
        try {
            instance = WorkflowInstance.runningLinear(instanceId, "WFI-" + instanceId,
                    definition, workflowVersion, resolverBinding,
                    command.businessType(), command.businessId(),
                    command.businessKey(), command.enterpriseId(), command.snapshotRef(),
                    command.snapshotHash(), command.attemptNo(), principal.userId(), principal.orgId(),
                    entry.id(), command.variablesSnapshot(), command.idempotencyKey(), requestHash,
                    MDC.get("traceId"), now);
            if (bindingService != null) {
                frozenBindings = bindingService.prepare(instance, definition, workflowVersion, nodes,
                        now, "{\"reasonCode\":\"INSTANCE_START\"}");
            }
            Long executionId = identityGenerator.nextId();
            execution = executionService.enter(executionId, "WFNE-" + executionId, instance,
                    entry, 1, null, null, MDC.get("traceId"), now);
            Long taskId = identityGenerator.nextId();
            AssignmentContext assignmentContext = assignmentContext(
                    taskId, instance, entry, execution, principal, now);
            cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding entryBinding =
                    frozenBindings == null ? null : frozenBindings.requireNode(entry.id());
            Long targetUserId = entryBinding == null ? assignment.resolve(entry)
                    : entryBinding.requireExplicitUserId();
            AssignmentResult assignmentResult = assignmentStrategy.resolve(assignmentContext, targetUserId);
            AssignmentResolver resolver = entryBinding == null
                    ? resolverService.selectForExistingInstance(instance.requireResolverVersionBinding())
                    : bindingService.runtimeResolver(frozenBindings.requireResolver(entryBinding.resolverBindingId()));
            requireCompatibleResolver(resolver, assignmentResult);
            CandidatePool candidatePool = resolver.resolve(
                    new AssignmentResolverContext(assignmentContext, assignmentResult));
            AssignmentResolutionResult resolution = AssignmentResolutionResult.direct(candidatePool);
            assignmentResult = resolution.toAssignmentResult();
            Long snapshotId = identityGenerator.nextId();
            assignmentSnapshot = AssignmentSnapshot.create(
                    snapshotId, assignmentContext, assignmentResult);
            LocalDateTime dueTime = entry.timeoutMinutes() == null
                    ? null : now.plusMinutes(entry.timeoutMinutes());
            task = WorkflowTask.pendingLinear(taskId, "WFT-" + taskId, instance, entry,
                    execution, resolution.selectedUserId(), candidatePool.targetSnapshot(), dueTime);
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
            throw invalid(exception);
        }
        instanceRepository.save(instance);
        if (frozenBindings != null) bindingService.save(frozenBindings);
        executionRepository.save(execution);
        taskRepository.save(task);
        saveAssignmentSnapshot(assignmentSnapshot);
        WorkflowInstance active = instance.pointTo(entry.id(), execution.id());
        instanceRepository.update(active);
        return active;
    }

    private WorkflowInstance freezeRoleInstance(
            StartWorkflowCommand command, SecurityPrincipal principal,
            WorkflowDefinition definition, WorkflowVersion workflowVersion,
            List<WorkflowNode> nodes, WorkflowNode entry, String graphHash,
            String requestHash, LocalDateTime now, Long instanceId) {
        if (bindingService == null || versionBindingFreezer == null) {
            throw new BusinessException("B2650", "Version resolver binding freeze is unavailable");
        }
        WorkflowInstance instance;
        WorkflowNodeExecution execution;
        WorkflowResolverBindingApplicationService.FrozenBindings frozen;
        try {
            instance = WorkflowInstance.runningLinear(instanceId, "WFI-" + instanceId,
                    definition, workflowVersion, command.businessType(), command.businessId(),
                    command.businessKey(), command.enterpriseId(), command.snapshotRef(),
                    command.snapshotHash(), command.attemptNo(), principal.userId(), principal.orgId(),
                    entry.id(), command.variablesSnapshot(), command.idempotencyKey(), requestHash,
                    MDC.get("traceId"), now);
            frozen = versionBindingFreezer.prepare(instance, definition, workflowVersion,
                    nodes, graphHash, now, "{\"reasonCode\":\"INSTANCE_START_ROLE_FREEZE\"}");
            Long executionId = identityGenerator.nextId();
            execution = executionService.enter(executionId, "WFNE-" + executionId,
                    instance, entry, 1, null, null, MDC.get("traceId"), now);
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
            throw invalid(exception);
        }
        instanceRepository.save(instance);
        bindingService.save(frozen);
        executionRepository.save(execution);
        WorkflowInstance active = instance.pointTo(entry.id(), execution.id());
        instanceRepository.update(active);
        return active;
    }

    private AssignmentContext assignmentContext(
            Long taskId, WorkflowInstance instance, WorkflowNode node,
            WorkflowNodeExecution execution, SecurityPrincipal principal, LocalDateTime now) {
        return new AssignmentContext(taskId, instance.id(), instance.versionId(), node.id(),
                execution.id(), instance.enterpriseId(), instance.initiatorUserId(),
                instance.initiatorOrgId(), String.valueOf(principal.userId()), now,
                MDC.get("traceId"));
    }

    private void saveAssignmentSnapshot(AssignmentSnapshot snapshot) {
        if (assignmentSnapshotRepository != null) {
            assignmentSnapshotRepository.save(snapshot);
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

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:view')")
    public WorkflowInstanceDetail queryInstance(Long instanceId) {
        WorkflowInstance instance = requireInstance(instanceId);
        requireAccessible(instance, securityContext.principal());
        return new WorkflowInstanceDetail(instance);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:view')")
    public List<WorkflowTask> queryTasks(Long instanceId) {
        WorkflowInstance instance = requireInstance(instanceId);
        requireAccessible(instance, securityContext.principal());
        return taskRepository.findByInstanceId(instanceId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:view')")
    public WorkflowResolverBindingDetail queryResolverBinding(Long instanceId) {
        WorkflowInstance instance = requireInstance(instanceId);
        requireAccessible(instance, securityContext.principal());
        try {
            return WorkflowResolverBindingDetail.from(instance.requireResolverVersionBinding());
        } catch (IllegalStateException exception) {
            throw new BusinessException("B2634", "workflow instance has no structured resolver binding");
        }
    }

    private void validateDefinition(WorkflowDefinition definition, StartWorkflowCommand command) {
        if (definition.status() != WorkflowDefinition.Status.ACTIVE || definition.currentVersionId() == null) {
            throw new BusinessException("B2510", "workflow definition is not active or has no published version");
        }
        if (!definition.enterpriseId().equals(command.enterpriseId())) {
            throw new BusinessException("B2513", "workflow definition belongs to another enterprise");
        }
        if (!definition.businessType().equals(command.businessType())) {
            throw new BusinessException("B2510", "workflow definition does not support this business type");
        }
    }

    private WorkflowInstance requireInstance(Long id) {
        return instanceRepository.findById(id).orElseThrow(() -> notFound("workflow instance"));
    }

    private void requireAccessible(WorkflowInstance instance, SecurityPrincipal principal) {
        boolean accessible = principal.allDataScope()
                || instance.initiatorUserId().equals(principal.userId())
                || (principal.allowedOrgIds() != null && principal.allowedOrgIds().contains(instance.initiatorOrgId()));
        if (!accessible) throw new BusinessException("B2513", "workflow instance is outside current data scope");
    }

    private void requireStartScope(WorkflowDefinition definition, SecurityPrincipal principal) {
        if (principal.allDataScope()) return;
        boolean ownsDefinitionScope = definition.ownerOrgId() != null
                && principal.allowedOrgIds() != null
                && principal.allowedOrgIds().contains(definition.ownerOrgId());
        if (!ownsDefinitionScope) {
            throw new BusinessException("B2513", "workflow definition is outside current organization scope");
        }
    }

    private String requestHash(StartWorkflowCommand command, SecurityPrincipal principal) {
        String canonical = String.join("\u001f",
                text(command.definitionId()), command.businessType(), command.businessId(), command.businessKey(),
                text(command.enterpriseId()), text(command.snapshotRef()), text(command.snapshotHash()),
                text(command.attemptNo()), text(command.variablesSnapshot()), command.idempotencyKey(),
                text(principal.userId()), text(principal.orgId()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String text(Object value) { return Objects.toString(value, ""); }
    private BusinessException notFound(String resource) { return new BusinessException("B2514", resource + " does not exist"); }
    private BusinessException invalid(RuntimeException cause) {
        BusinessException exception = new BusinessException("B2510", cause.getMessage());
        exception.initCause(cause);
        return exception;
    }
}
