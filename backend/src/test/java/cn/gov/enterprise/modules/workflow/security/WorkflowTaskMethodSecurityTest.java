package cn.gov.enterprise.modules.workflow.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.ProcessWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowTaskActionApplicationService;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskActionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
@ContextConfiguration(classes = WorkflowTaskMethodSecurityTest.TestConfig.class)
class WorkflowTaskMethodSecurityTest {
    @Autowired private WorkflowTaskActionApplicationService service;
    @Autowired private WorkflowTaskRepository tasks;
    @Autowired private WorkflowInstanceRepository instances;
    @Autowired private WorkflowTaskActionRepository actions;
    @Autowired private CurrentSecurityContext securityContext;

    @BeforeEach
    void resetMocks() {
        org.mockito.Mockito.reset(tasks, instances, actions, securityContext);
    }

    @Test
    @WithMockUser(authorities = "workflow:view")
    void userWithoutApproveAuthorityMustBeRejectedBeforeTaskAccess() {
        assertThatThrownBy(() -> service.approve(11L, command()))
                .isInstanceOf(AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(tasks);
    }

    @Test
    @WithMockUser(authorities = "workflow:approve")
    void approveAuthorityMustNotOverrideTaskAssignee() {
        WorkflowInstance instance = instance();
        WorkflowTask assignedElsewhere = new WorkflowTask(11L, "WFT-11", 10L, 2L, 3L,
                "FIRST", "首节点", 1, "FIRST:1", 99L, "{}",
                WorkflowTask.Status.PENDING, "APPROVE,REJECT", null, null,
                null, null, null, 0);
        when(securityContext.principal()).thenReturn(new SecurityPrincipal(
                20L, "operator", 30L, Set.of(30L), false, false, 0));
        when(tasks.findById(11L)).thenReturn(Optional.of(assignedElsewhere));
        when(instances.findById(10L)).thenReturn(Optional.of(instance));
        when(actions.findByTaskIdAndIdempotencyKey(11L, "action-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(11L, command()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("assigned to another user");
        org.mockito.Mockito.verify(tasks, org.mockito.Mockito.never())
                .update(org.mockito.ArgumentMatchers.any());
    }

    private ProcessWorkflowTaskCommand command() {
        return new ProcessWorkflowTaskCommand("同意", "action-1");
    }

    private WorkflowInstance instance() {
        LocalDateTime now = LocalDateTime.now();
        WorkflowDefinition definition = new WorkflowDefinition(1L, "CONTRACT_APPROVAL", "合同审批",
                "CONTRACT", 100L, 30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 1);
        WorkflowVersion version = new WorkflowVersion(2L, 1L, 1, WorkflowVersion.Status.PUBLISHED,
                "1.0", "hash", null, now, null, 20L, now, null, 1);
        return WorkflowInstance.running(10L, "WFI-10", definition, version, "CONTRACT", "B-1",
                "CONTRACT:B-1", 100L, null, null, 1, 20L, 30L, 3L,
                null, "start-1", "request-hash", null, now);
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean WorkflowTaskRepository tasks() { return org.mockito.Mockito.mock(WorkflowTaskRepository.class); }
        @Bean WorkflowInstanceRepository instances() { return org.mockito.Mockito.mock(WorkflowInstanceRepository.class); }
        @Bean WorkflowNodeRepository nodes() { return org.mockito.Mockito.mock(WorkflowNodeRepository.class); }
        @Bean WorkflowTaskActionRepository actions() { return org.mockito.Mockito.mock(WorkflowTaskActionRepository.class); }
        @Bean WorkflowIdentityGenerator ids() { return org.mockito.Mockito.mock(WorkflowIdentityGenerator.class); }
        @Bean CurrentSecurityContext securityContext() { return org.mockito.Mockito.mock(CurrentSecurityContext.class); }
        @Bean WorkflowTaskActionApplicationService service(
                WorkflowTaskRepository tasks, WorkflowInstanceRepository instances,
                WorkflowNodeRepository nodes, WorkflowTaskActionRepository actions,
                WorkflowIdentityGenerator ids, CurrentSecurityContext securityContext) {
            return new WorkflowTaskActionApplicationService(
                    tasks, instances, nodes, actions, ids, securityContext);
        }
    }
}
