package cn.gov.enterprise.modules.workflow.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.system.log.service.AuditLogService;
import cn.gov.enterprise.modules.workflow.application.command.ClaimWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.application.service.TaskClaimApplicationService;
import cn.gov.enterprise.modules.workflow.application.service.TaskClaimTransactionService;
import cn.gov.enterprise.security.CurrentSecurityContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = WorkflowTaskClaimMethodSecurityTest.Config.class)
class WorkflowTaskClaimMethodSecurityTest {
    @Autowired TaskClaimApplicationService service;
    @Autowired TaskClaimTransactionService transaction;

    @Test
    @WithMockUser(authorities = "workflow:view")
    void rbacMustRejectClaimBeforeTransactionAccess() {
        assertThatThrownBy(() -> service.claim(1L,
                new ClaimWorkflowTaskCommand("key", null, null)))
                .isInstanceOf(AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(transaction);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean TaskClaimTransactionService transaction() {
            return org.mockito.Mockito.mock(TaskClaimTransactionService.class);
        }
        @Bean CurrentSecurityContext security() { return org.mockito.Mockito.mock(CurrentSecurityContext.class); }
        @Bean AuditLogService audit() { return org.mockito.Mockito.mock(AuditLogService.class); }
        @Bean TaskClaimApplicationService service(TaskClaimTransactionService transaction,
                CurrentSecurityContext security, AuditLogService audit) {
            return new TaskClaimApplicationService(transaction, security, audit);
        }
    }
}
