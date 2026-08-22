package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowNodeExecutionCommand;
import cn.gov.enterprise.modules.workflow.application.command.UpdateWorkflowNodeExecutionCommand;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Foundation operations only; this service does not advance an instance to the next node. */
@Service
public class WorkflowMultiNodeRuntimeApplicationService {
    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowVersionRepository versionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowTransitionRepository transitionRepository;
    private final WorkflowNodeExecutionRepository executionRepository;
    private final WorkflowIdentityGenerator identityGenerator;
    private final CurrentSecurityContext securityContext;

    public WorkflowMultiNodeRuntimeApplicationService(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowVersionRepository versionRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowTransitionRepository transitionRepository,
            WorkflowNodeExecutionRepository executionRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.instanceRepository = instanceRepository;
        this.nodeRepository = nodeRepository;
        this.transitionRepository = transitionRepository;
        this.executionRepository = executionRepository;
        this.identityGenerator = identityGenerator;
        this.securityContext = securityContext;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:definition:view')")
    public List<WorkflowTransition> queryTransitions(Long definitionId, Long versionId) {
        WorkflowDefinition definition = requireDefinition(definitionId);
        WorkflowVersion workflowVersion = versionRepository.findById(versionId)
                .orElseThrow(() -> notFound("workflow version"));
        if (!definitionId.equals(workflowVersion.definitionId())) {
            throw new BusinessException("B2600", "workflow version belongs to another definition");
        }
        requireDefinitionAccessible(definition, securityContext.principal());
        return transitionRepository.findByVersionId(versionId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:view')")
    public List<WorkflowNodeExecution> queryNodeExecutions(Long instanceId) {
        WorkflowInstance instance = requireInstance(instanceId);
        requireInstanceAccessible(instance, securityContext.principal());
        return executionRepository.findByInstanceId(instanceId);
    }

    /** Internal engine boundary; no REST endpoint is permitted for this operation. */
    @Transactional
    public WorkflowNodeExecution createNodeExecution(CreateWorkflowNodeExecutionCommand command) {
        Objects.requireNonNull(command, "command");
        WorkflowInstance instance = requireInstance(command.instanceId());
        SecurityPrincipal principal = securityContext.principal();
        requireInstanceAccessible(instance, principal);
        if (instance.status() != WorkflowInstance.Status.RUNNING) {
            throw new BusinessException("B2602", "workflow instance is not running");
        }
        WorkflowNode node = nodeRepository.findById(command.nodeId())
                .orElseThrow(() -> notFound("workflow node"));
        if (!instance.versionId().equals(node.versionId()) || !node.enabled()) {
            throw new BusinessException("B2600", "workflow node is disabled or belongs to another version");
        }
        if (command.visitNo() <= 0) throw new BusinessException("B2600", "visitNo must be positive");

        WorkflowNodeExecution existing = executionRepository
                .findByInstanceIdAndNodeIdAndVisitNo(instance.id(), node.id(), command.visitNo())
                .orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.previousExecutionId(), command.previousExecutionId())
                    || !Objects.equals(existing.sourceTransitionId(), command.sourceTransitionId())) {
                throw new BusinessException("B2609", "node visit already exists with different path evidence");
            }
            return existing;
        }

        validatePathEvidence(instance, node, command.previousExecutionId(), command.sourceTransitionId());
        try {
            Long id = identityGenerator.nextId();
            WorkflowNodeExecution execution = WorkflowNodeExecution.created(
                    id, "WFNE-" + id, instance, node, command.visitNo(),
                    command.previousExecutionId(), command.sourceTransitionId(),
                    command.traceId(), LocalDateTime.now());
            executionRepository.save(execution);
            return execution;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid(exception);
        }
    }

    /** Internal engine boundary; no REST endpoint is permitted for this operation. */
    @Transactional
    public WorkflowNodeExecution updateNodeExecutionState(UpdateWorkflowNodeExecutionCommand command) {
        Objects.requireNonNull(command, "command");
        WorkflowNodeExecution current = executionRepository.findById(command.executionId())
                .orElseThrow(() -> notFound("workflow node execution"));
        WorkflowInstance instance = requireInstance(current.instanceId());
        SecurityPrincipal principal = securityContext.principal();
        requireInstanceAccessible(instance, principal);
        if (current.version() != command.expectedVersion()) {
            throw concurrentChange();
        }
        LocalDateTime now = LocalDateTime.now();
        WorkflowNodeExecution changed;
        try {
            changed = switch (Objects.requireNonNull(command.targetStatus(), "targetStatus")) {
                case ACTIVE -> current.activate(now);
                case COMPLETED -> current.complete(principal.userId(), command.result(), now);
                case REJECTED -> current.reject(principal.userId(), command.result(), now);
                case CANCELLED -> current.cancel(principal.userId(), command.result(), now);
                case FAILED -> current.fail(command.failureCode(), now);
                case CREATED -> throw new IllegalStateException("node execution cannot return to CREATED");
            };
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
            throw invalid(exception);
        }
        if (!executionRepository.updateState(changed, command.expectedVersion())) throw concurrentChange();
        return changed;
    }

    private void validatePathEvidence(
            WorkflowInstance instance, WorkflowNode targetNode,
            Long previousExecutionId, Long sourceTransitionId) {
        if ((previousExecutionId == null) != (sourceTransitionId == null)) {
            throw new BusinessException("B2600", "previous execution and source transition must appear together");
        }
        if (previousExecutionId == null) return;
        WorkflowNodeExecution previous = executionRepository.findById(previousExecutionId)
                .orElseThrow(() -> notFound("previous node execution"));
        if (!instance.id().equals(previous.instanceId()) || !isRoutableTerminal(previous.status())) {
            throw new BusinessException("B2600", "previous node execution is not terminal in this instance");
        }
        WorkflowTransition transition = transitionRepository.findById(sourceTransitionId)
                .orElseThrow(() -> notFound("source workflow transition"));
        if (!instance.versionId().equals(transition.versionId())
                || !previous.nodeId().equals(transition.fromNodeId())
                || !targetNode.id().equals(transition.toNodeId()) || !transition.enabled()
                || transition.triggerType() != expectedTrigger(previous.status())) {
            throw new BusinessException("B2600", "source transition does not match the execution path");
        }
    }

    private boolean isRoutableTerminal(WorkflowNodeExecution.Status status) {
        return status == WorkflowNodeExecution.Status.COMPLETED
                || status == WorkflowNodeExecution.Status.REJECTED;
    }

    private WorkflowTransition.TriggerType expectedTrigger(WorkflowNodeExecution.Status status) {
        return status == WorkflowNodeExecution.Status.COMPLETED
                ? WorkflowTransition.TriggerType.APPROVE : WorkflowTransition.TriggerType.REJECT;
    }

    private WorkflowDefinition requireDefinition(Long id) {
        return definitionRepository.findById(id).orElseThrow(() -> notFound("workflow definition"));
    }

    private WorkflowInstance requireInstance(Long id) {
        return instanceRepository.findById(id).orElseThrow(() -> notFound("workflow instance"));
    }

    private void requireDefinitionAccessible(WorkflowDefinition definition, SecurityPrincipal principal) {
        if (principal.allDataScope()) return;
        if (definition.ownerOrgId() == null || principal.allowedOrgIds() == null
                || !principal.allowedOrgIds().contains(definition.ownerOrgId())) {
            throw new BusinessException("B2613", "workflow definition is outside current data scope");
        }
    }

    private void requireInstanceAccessible(WorkflowInstance instance, SecurityPrincipal principal) {
        boolean accessible = principal.allDataScope()
                || instance.initiatorUserId().equals(principal.userId())
                || (principal.allowedOrgIds() != null
                    && principal.allowedOrgIds().contains(instance.initiatorOrgId()));
        if (!accessible) throw new BusinessException("B2613", "workflow instance is outside current data scope");
    }

    private BusinessException notFound(String resource) {
        return new BusinessException("B2604", resource + " does not exist");
    }

    private BusinessException invalid(RuntimeException cause) {
        BusinessException exception = new BusinessException("B2600", cause.getMessage());
        exception.initCause(cause);
        return exception;
    }

    private BusinessException concurrentChange() {
        return new BusinessException("B2629", "workflow node execution was changed concurrently");
    }
}
