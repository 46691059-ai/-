package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.modules.system.log.service.AuditLogCommand;
import cn.gov.enterprise.modules.system.log.service.AuditLogService;
import cn.gov.enterprise.modules.system.log.support.AuditRequestSupport;
import cn.gov.enterprise.modules.workflow.application.command.ClaimWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaimResult;
import cn.gov.enterprise.security.CurrentSecurityContext;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class TaskClaimApplicationService {
    private final TaskClaimTransactionService transactionService;
    private final CurrentSecurityContext security;
    private final AuditLogService auditLog;

    public TaskClaimApplicationService(TaskClaimTransactionService transactionService,
            CurrentSecurityContext security, AuditLogService auditLog) {
        this.transactionService = transactionService;
        this.security = security; this.auditLog = auditLog;
    }

    @PreAuthorize("hasAuthority('workflow:approve')")
    public TaskClaimResult claim(Long taskId, ClaimWorkflowTaskCommand command) {
        String traceId = normalizeTrace(command.traceId());
        try {
            return transactionService.claim(taskId, command, security.principal(), traceId);
        } catch (RuntimeException exception) {
            auditLog.save(new AuditLogCommand(security.userId(), security.username(),
                    "SECURITY", "WORKFLOW", "TASK_CLAIM_REJECTED",
                    "/workflow/tasks/" + taskId + "/claim", "POST",
                    "taskId=" + taskId, "DENIED", null, "FAIL",
                    exception.getMessage(), 0L, traceId));
            throw exception;
        }
    }

    private String normalizeTrace(String supplied) {
        String value = supplied == null || supplied.isBlank() ? AuditRequestSupport.traceId() : supplied.trim();
        return value == null || value.isBlank() ? UUID.randomUUID().toString().replace("-", "") : value;
    }
}
