package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.ProcessWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTaskAction;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskActionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional command boundary for Workflow Lite terminal task actions. */
@Service
public class WorkflowTaskActionApplicationService {
    private final WorkflowTaskRepository taskRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowTaskActionRepository actionRepository;
    private final WorkflowIdentityGenerator identityGenerator;
    private final CurrentSecurityContext securityContext;

    public WorkflowTaskActionApplicationService(
            WorkflowTaskRepository taskRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowTaskActionRepository actionRepository,
            WorkflowIdentityGenerator identityGenerator,
            CurrentSecurityContext securityContext) {
        this.taskRepository = taskRepository;
        this.instanceRepository = instanceRepository;
        this.nodeRepository = nodeRepository;
        this.actionRepository = actionRepository;
        this.identityGenerator = identityGenerator;
        this.securityContext = securityContext;
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:approve')")
    public WorkflowTaskAction approve(Long taskId, ProcessWorkflowTaskCommand command) {
        return process(taskId, command, WorkflowTaskAction.ActionType.APPROVE);
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:approve')")
    public WorkflowTaskAction reject(Long taskId, ProcessWorkflowTaskCommand command) {
        return process(taskId, command, WorkflowTaskAction.ActionType.REJECT);
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:withdraw')")
    public WorkflowTaskAction withdraw(Long taskId, ProcessWorkflowTaskCommand command) {
        return process(taskId, command, WorkflowTaskAction.ActionType.WITHDRAW);
    }

    private WorkflowTaskAction process(
            Long taskId, ProcessWorkflowTaskCommand command, WorkflowTaskAction.ActionType actionType) {
        SecurityPrincipal principal = securityContext.principal();
        WorkflowTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> notFound("workflow task"));
        WorkflowInstance instance = instanceRepository.findById(task.instanceId())
                .orElseThrow(() -> notFound("workflow instance"));
        if (!instance.id().equals(task.instanceId()) || !instance.versionId().equals(task.versionId())) {
            throw new BusinessException("B2520", "workflow task does not belong to instance version");
        }
        requireAccessible(instance, principal);
        if (instance.engineMode() == WorkflowEngineMode.MULTI_NODE_LINEAR_V1) {
            throw new BusinessException("B2612", "linear workflow tasks must use the complete endpoint");
        }

        String requestHash = requestHash(taskId, command, actionType, principal);
        WorkflowTaskAction existing = actionRepository
                .findByTaskIdAndIdempotencyKey(taskId, command.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)
                    || existing.actionType() != actionType
                    || !existing.operatorUserId().equals(principal.userId())) {
                throw new BusinessException("B2529", "idempotency key was used by a different task action");
            }
            return existing;
        }

        if (instance.status() != WorkflowInstance.Status.RUNNING) {
            throw new BusinessException("B2522", "workflow instance is not running");
        }
        if (actionType == WorkflowTaskAction.ActionType.WITHDRAW) {
            requireWithdrawalAllowed(task, instance, principal);
        } else {
            requireApprover(task, principal);
        }

        LocalDateTime now = LocalDateTime.now();
        WorkflowTask changedTask;
        WorkflowInstance changedInstance;
        try {
            changedTask = switch (actionType) {
                case APPROVE -> task.approve(principal.userId(), now);
                case REJECT -> task.reject(principal.userId(), now);
                case WITHDRAW -> task.withdraw(principal.userId(), now);
            };
            changedInstance = switch (actionType) {
                case APPROVE -> instance.approve(now);
                case REJECT -> instance.reject(now);
                case WITHDRAW -> instance.withdraw(now);
            };
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
            throw invalidTransition(exception);
        }

        Long actionId = identityGenerator.nextId();
        WorkflowTaskAction action = WorkflowTaskAction.create(
                actionId, "WFA-" + actionId, task, actionType, principal.userId(), principal.orgId(),
                command.comment(), now, command.idempotencyKey(), requestHash, MDC.get("traceId"));
        taskRepository.update(changedTask);
        instanceRepository.update(changedInstance);
        actionRepository.save(action);
        return action;
    }

    private void requireApprover(WorkflowTask task, SecurityPrincipal principal) {
        if (task.assignmentMode() == WorkflowTask.AssignmentMode.CANDIDATE_POOL) {
            throw new BusinessException("B2662", "Candidate Pool Claim is not implemented");
        }
        if (task.assigneeUserId() != null && !task.assigneeUserId().equals(principal.userId())) {
            throw new BusinessException("B2523", "workflow task is assigned to another user");
        }
    }

    private void requireWithdrawalAllowed(
            WorkflowTask task, WorkflowInstance instance, SecurityPrincipal principal) {
        if (!instance.initiatorUserId().equals(principal.userId())) {
            throw new BusinessException("B2523", "only the workflow initiator can withdraw this task");
        }
        WorkflowNode node = nodeRepository.findById(task.nodeId())
                .orElseThrow(() -> notFound("workflow node"));
        if (!node.versionId().equals(task.versionId()) || !node.withdrawAllowed()) {
            throw new BusinessException("B2523", "workflow node does not allow withdrawal");
        }
    }

    private void requireAccessible(WorkflowInstance instance, SecurityPrincipal principal) {
        boolean accessible = principal.allDataScope()
                || instance.initiatorUserId().equals(principal.userId())
                || (principal.allowedOrgIds() != null
                    && principal.allowedOrgIds().contains(instance.initiatorOrgId()));
        if (!accessible) throw new BusinessException("B2523", "workflow task is outside current data scope");
    }

    private String requestHash(
            Long taskId, ProcessWorkflowTaskCommand command,
            WorkflowTaskAction.ActionType actionType, SecurityPrincipal principal) {
        String canonical = String.join("\u001f", Objects.toString(taskId, ""), actionType.name(),
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
        return new BusinessException("B2524", resource + " does not exist");
    }

    private BusinessException invalidTransition(RuntimeException cause) {
        BusinessException exception = new BusinessException("B2522", cause.getMessage());
        exception.initCause(cause);
        return exception;
    }
}
