package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.PublishWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.command.ReplaceWorkflowNodesCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowDefinitionApplicationService;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionReleaseRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowVersionReleaseApplicationServiceTest {
    private final WorkflowDefinitionRepository definitions = org.mockito.Mockito.mock(WorkflowDefinitionRepository.class);
    private final WorkflowVersionRepository versions = org.mockito.Mockito.mock(WorkflowVersionRepository.class);
    private final WorkflowNodeRepository nodes = org.mockito.Mockito.mock(WorkflowNodeRepository.class);
    private final WorkflowIdentityGenerator ids = org.mockito.Mockito.mock(WorkflowIdentityGenerator.class);
    private final WorkflowVersionReleaseRepository releases = org.mockito.Mockito.mock(WorkflowVersionReleaseRepository.class);
    private final CurrentSecurityContext security = org.mockito.Mockito.mock(CurrentSecurityContext.class);
    private WorkflowDefinitionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowDefinitionApplicationService(definitions, versions, nodes, ids, releases, security);
        when(security.principal()).thenReturn(new SecurityPrincipal(7L, "publisher", 8L, Set.of(8L), false, false, 0));
        when(ids.nextId()).thenReturn(99L);
    }

    @Test
    void shouldPublishDraftAndWriteStableAudit() {
        WorkflowDefinition definition = draftDefinition(0);
        WorkflowVersion target = WorkflowVersion.draft(2L, 1L, 1, "1.0", "initial", null);
        when(definitions.findByIdForUpdate(1L)).thenReturn(Optional.of(definition));
        when(versions.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(nodes.findByVersionId(2L)).thenReturn(List.of(node(2L)));
        when(versions.updateState(any(), eq(WorkflowVersion.Status.DRAFT), eq(0))).thenReturn(true);
        when(definitions.updateCurrentVersion(any(), eq(0))).thenReturn(true);

        var result = service.publishVersion(1L, 2L, new PublishWorkflowVersionCommand(0, 0, "approved"));

        assertThat(result.version().status()).isEqualTo(WorkflowVersion.Status.PUBLISHED);
        assertThat(result.version().contentHash()).matches("[0-9a-f]{64}");
        ArgumentCaptor<WorkflowVersionRelease> audit = ArgumentCaptor.forClass(WorkflowVersionRelease.class);
        verify(releases).save(audit.capture());
        assertThat(audit.getValue().previousVersionId()).isNull();
        assertThat(audit.getValue().publishedVersionId()).isEqualTo(2L);
        assertThat(audit.getValue().contentHash()).isEqualTo(result.version().contentHash());
    }

    @Test
    void shouldRetirePreviousVersionInSameUseCase() {
        WorkflowDefinition definition = new WorkflowDefinition(1L, "WF", "Workflow", "TEST", 10L, 8L,
                WorkflowDefinition.Status.ACTIVE, 3L, null, 4);
        WorkflowVersion target = WorkflowVersion.draft(2L, 1L, 2, "1.0", "next", 3L);
        WorkflowVersion previous = published(3L, 1);
        when(definitions.findByIdForUpdate(1L)).thenReturn(Optional.of(definition));
        when(versions.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(versions.findByIdForUpdate(3L)).thenReturn(Optional.of(previous));
        when(nodes.findByVersionId(2L)).thenReturn(List.of(node(2L)));
        when(versions.updateState(any(), any(), any(Integer.class))).thenReturn(true);
        when(definitions.updateCurrentVersion(any(), eq(4))).thenReturn(true);

        service.publishVersion(1L, 2L, new PublishWorkflowVersionCommand(4, 0, null));

        verify(versions).updateState(any(), eq(WorkflowVersion.Status.PUBLISHED), eq(1));
        ArgumentCaptor<WorkflowVersionRelease> audit = ArgumentCaptor.forClass(WorkflowVersionRelease.class);
        verify(releases).save(audit.capture());
        assertThat(audit.getValue().previousVersionId()).isEqualTo(3L);
    }

    @Test
    void shouldRejectInvalidGraphWithoutWritingState() {
        when(definitions.findByIdForUpdate(1L)).thenReturn(Optional.of(draftDefinition(0)));
        when(versions.findByIdForUpdate(2L)).thenReturn(Optional.of(
                WorkflowVersion.draft(2L, 1L, 1, "1.0", null, null)));
        when(nodes.findByVersionId(2L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.publishVersion(
                1L, 2L, new PublishWorkflowVersionCommand(0, 0, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("exactly one");
        verify(versions, never()).updateState(any(), any(), any(Integer.class));
        verify(releases, never()).save(any());
    }

    @Test
    void staleExpectedVersionShouldCloseConcurrentPublication() {
        when(definitions.findByIdForUpdate(1L)).thenReturn(Optional.of(draftDefinition(1)));
        assertThatThrownBy(() -> service.publishVersion(
                1L, 2L, new PublishWorkflowVersionCommand(0, 0, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("concurrently");
        verify(versions, never()).findByIdForUpdate(2L);
    }

    @Test
    void cloneShouldAllocateMonotonicVersionAndNewNodeIdentityWithoutChangingSource() {
        WorkflowVersion source = published(3L, 1);
        WorkflowNode sourceNode = node(3L);
        when(definitions.findByIdForUpdate(1L)).thenReturn(Optional.of(draftDefinition(0)));
        when(versions.findById(3L)).thenReturn(Optional.of(source));
        when(versions.nextVersionNo(1L)).thenReturn(2);
        when(nodes.findByVersionId(3L)).thenReturn(List.of(sourceNode));
        when(ids.nextId()).thenReturn(20L, 21L);

        WorkflowVersion clone = service.createVersion(1L, new CreateWorkflowVersionCommand("1.0", "clone", 3L));

        assertThat(clone.id()).isEqualTo(20L);
        assertThat(clone.versionNo()).isEqualTo(2);
        assertThat(clone.status()).isEqualTo(WorkflowVersion.Status.DRAFT);
        assertThat(source.status()).isEqualTo(WorkflowVersion.Status.PUBLISHED);
        verify(nodes).replaceNodes(eq(20L), org.mockito.ArgumentMatchers.argThat(copied ->
                copied.size() == 1 && copied.getFirst().id().equals(21L)
                        && copied.getFirst().versionId().equals(20L)));
    }

    @Test
    void publishedVersionNodesMustRemainImmutable() {
        WorkflowVersion source = published(3L, 1);
        when(definitions.findById(1L)).thenReturn(Optional.of(draftDefinition(0)));
        when(versions.findByIdForUpdate(3L)).thenReturn(Optional.of(source));
        var command = new ReplaceWorkflowNodesCommand(List.of(new ReplaceWorkflowNodesCommand.NodeCommand(
                "APPROVE", "Approve", WorkflowNode.NodeType.APPROVAL, 1,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL, WorkflowNode.ApprovalMode.SINGLE, null,
                WorkflowNode.AssignmentRuleType.USER, "{}", null, null, null, true, true)));

        assertThatThrownBy(() -> service.replaceNodes(1L, 3L, command))
                .isInstanceOf(BusinessException.class).hasMessageContaining("immutable");
        verify(nodes, never()).replaceNodes(any(), any());
    }

    private WorkflowDefinition draftDefinition(int version) {
        return new WorkflowDefinition(1L, "WF", "Workflow", "TEST", 10L, 8L,
                WorkflowDefinition.Status.DRAFT, null, null, version);
    }

    private WorkflowVersion published(Long id, int optimisticVersion) {
        var now = java.time.LocalDateTime.now().minusDays(1);
        return new WorkflowVersion(id, 1L, 1, WorkflowVersion.Status.PUBLISHED, "1.0", "a".repeat(64),
                null, now, null, 7L, now, null, optimisticVersion);
    }

    private WorkflowNode node(Long versionId) {
        return new WorkflowNode(5L, versionId, "APPROVE", "Approve", WorkflowNode.NodeType.APPROVAL, 1,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL, WorkflowNode.ApprovalMode.SINGLE, null,
                WorkflowNode.AssignmentRuleType.USER, "{\"userId\":7}", null, null,
                null, true, true, 0);
    }
}
