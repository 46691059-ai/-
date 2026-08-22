package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowAuditedEntity;
import cn.gov.enterprise.security.CurrentSecurityContext;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class WorkflowPersistenceAudit {
    private final CurrentSecurityContext securityContext;

    public WorkflowPersistenceAudit(CurrentSecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    public String operator() {
        return securityContext.username();
    }

    public void initialize(WorkflowAuditedEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        String operator = operator();
        entity.setCreatedBy(operator);
        entity.setCreatedTime(now);
        entity.setUpdatedBy(operator);
        entity.setUpdatedTime(now);
        entity.setDeleted(0);
        entity.setDeleteToken(0L);
        entity.setVersion(0);
    }

    public void touch(WorkflowAuditedEntity entity) {
        entity.setUpdatedBy(operator());
        entity.setUpdatedTime(LocalDateTime.now());
    }
}
