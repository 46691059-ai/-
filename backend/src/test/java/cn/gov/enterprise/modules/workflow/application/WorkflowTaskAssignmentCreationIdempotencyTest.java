package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.application.command.StartWorkflowCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowRuntimeApplicationService;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowTaskAssignmentCreationIdempotencyTest {
    @Test
    void repeatedStartMustReturnExistingInstanceWithoutNewTaskOrSnapshot() throws Exception {
        WorkflowDefinitionRepository definitions = org.mockito.Mockito.mock(WorkflowDefinitionRepository.class);
        WorkflowVersionRepository versions = org.mockito.Mockito.mock(WorkflowVersionRepository.class);
        WorkflowNodeRepository nodes = org.mockito.Mockito.mock(WorkflowNodeRepository.class);
        WorkflowInstanceRepository instances = org.mockito.Mockito.mock(WorkflowInstanceRepository.class);
        WorkflowTaskRepository tasks = org.mockito.Mockito.mock(WorkflowTaskRepository.class);
        WorkflowIdentityGenerator ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
        CurrentSecurityContext security = org.mockito.Mockito.mock(CurrentSecurityContext.class);
        WorkflowNodeExecutionRepository executions = org.mockito.Mockito.mock(WorkflowNodeExecutionRepository.class);
        WorkflowTransitionRepository transitions = org.mockito.Mockito.mock(WorkflowTransitionRepository.class);
        WorkflowTaskAssignmentSnapshotRepository snapshots =
                org.mockito.Mockito.mock(WorkflowTaskAssignmentSnapshotRepository.class);
        StartWorkflowCommand command = new StartWorkflowCommand(1L, "TEST", "B-1", "TEST:B-1",
                100L, null, null, 1, "{}", "same-start");
        SecurityPrincipal principal = new SecurityPrincipal(
                20L, "starter", 30L, Set.of(30L), false, false, 0);
        WorkflowInstance existing = existingInstance(command, principal);
        when(security.principal()).thenReturn(principal);
        when(instances.findByEnterpriseIdAndIdempotencyKey(100L, "same-start"))
                .thenReturn(Optional.of(existing));
        WorkflowRuntimeApplicationService service = new WorkflowRuntimeApplicationService(
                definitions, versions, nodes, instances, tasks, ids, security,
                executions, transitions, snapshots);

        assertThat(service.startWorkflow(command)).isSameAs(existing);
        verify(tasks, never()).save(org.mockito.ArgumentMatchers.any());
        verify(snapshots, never()).save(org.mockito.ArgumentMatchers.any());
        verify(ids, never()).nextId();
    }

    private WorkflowInstance existingInstance(
            StartWorkflowCommand command, SecurityPrincipal principal) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        WorkflowDefinition definition = new WorkflowDefinition(1L, "TEST", "Test", "TEST",
                100L, 30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 0);
        WorkflowVersion version = new WorkflowVersion(2L, 1L, 1,
                WorkflowVersion.Status.PUBLISHED, "1.0", "hash", null, now, null,
                20L, now, null, 0);
        return WorkflowInstance.running(10L, "WFI-10", definition, version, "TEST", "B-1",
                "TEST:B-1", 100L, null, null, 1, 20L, 30L, 3L, "{}",
                "same-start", requestHash(command, principal), null, now);
    }

    private String requestHash(StartWorkflowCommand command, SecurityPrincipal principal) throws Exception {
        String canonical = String.join("\u001f", text(command.definitionId()), command.businessType(),
                command.businessId(), command.businessKey(), text(command.enterpriseId()),
                text(command.snapshotRef()), text(command.snapshotHash()), text(command.attemptNo()),
                text(command.variablesSnapshot()), command.idempotencyKey(), text(principal.userId()),
                text(principal.orgId()));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private String text(Object value) {
        return Objects.toString(value, "");
    }
}
