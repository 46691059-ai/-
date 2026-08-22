package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowTaskAssignmentApplicationService;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowAssignmentResolutionDetail;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowTaskAssignmentDetail;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentContext;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserAssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowTaskAssignmentApplicationServiceTest {
    private final WorkflowTaskRepository tasks = org.mockito.Mockito.mock(WorkflowTaskRepository.class);
    private final WorkflowTaskAssignmentSnapshotRepository snapshots =
            org.mockito.Mockito.mock(WorkflowTaskAssignmentSnapshotRepository.class);
    private final WorkflowInstanceRepository instances = org.mockito.Mockito.mock(WorkflowInstanceRepository.class);
    private final CurrentSecurityContext security = org.mockito.Mockito.mock(CurrentSecurityContext.class);
    private final WorkflowTaskAssignmentApplicationService service =
            new WorkflowTaskAssignmentApplicationService(tasks, snapshots, instances, security);

    @Test
    void queryMustReturnStructuredSnapshot() {
        fixture(new SecurityPrincipal(20L, "viewer", 30L, Set.of(30L), false, false, 0));
        when(snapshots.findByTaskId(11L)).thenReturn(Optional.of(snapshot()));

        WorkflowTaskAssignmentDetail detail = service.query(11L);

        assertThat(detail.source()).isEqualTo(WorkflowTaskAssignmentDetail.Source.STRUCTURED_V262);
        assertThat(detail.resolvedUserIds()).containsExactly(20L);
        assertThat(detail.strategyType()).isEqualTo("USER");
    }

    @Test
    void taskWithoutSnapshotMustUseLegacyProjectionWithoutInference() {
        fixture(new SecurityPrincipal(20L, "viewer", 30L, Set.of(30L), false, false, 0));
        when(snapshots.findByTaskId(11L)).thenReturn(Optional.empty());

        WorkflowTaskAssignmentDetail detail = service.query(11L);

        assertThat(detail.source()).isEqualTo(WorkflowTaskAssignmentDetail.Source.LEGACY_TASK_FIELDS);
        assertThat(detail.snapshotId()).isNull();
        assertThat(detail.strategyType()).isEqualTo("LEGACY_DIRECT_USER");
        assertThat(detail.resolvedUserIds()).containsExactly(20L);
    }

    @Test
    void queryResolutionMustReturnFrozenResolverCandidates() {
        fixture(new SecurityPrincipal(20L, "viewer", 30L, Set.of(30L), false, false, 0));
        when(snapshots.findByTaskId(11L)).thenReturn(Optional.of(snapshot()));

        WorkflowAssignmentResolutionDetail detail = service.queryResolution(11L);

        assertThat(detail.source())
                .isEqualTo(WorkflowAssignmentResolutionDetail.Source.STRUCTURED_SNAPSHOT);
        assertThat(detail.selectionMode()).isEqualTo("DIRECT");
        assertThat(detail.candidates()).extracting(
                WorkflowAssignmentResolutionDetail.Candidate::userId).containsExactly(20L);
        assertThat(detail.selectedUserId()).isEqualTo(20L);
    }

    @Test
    void queryResolutionMustKeepLegacyTaskReadableWithoutRerunningResolver() {
        fixture(new SecurityPrincipal(20L, "viewer", 30L, Set.of(30L), false, false, 0));
        when(snapshots.findByTaskId(11L)).thenReturn(Optional.empty());

        WorkflowAssignmentResolutionDetail detail = service.queryResolution(11L);

        assertThat(detail.source())
                .isEqualTo(WorkflowAssignmentResolutionDetail.Source.LEGACY_TASK_FIELDS);
        assertThat(detail.resolverVersion()).isEqualTo("LEGACY_UNVERSIONED");
        assertThat(detail.selectedUserId()).isEqualTo(20L);
    }

    @Test
    void queryMustEnforceInstanceDataScope() {
        fixture(new SecurityPrincipal(99L, "outsider", 40L, Set.of(40L), false, false, 0));
        assertThatThrownBy(() -> service.query(11L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("outside current data scope");
    }

    private void fixture(SecurityPrincipal principal) {
        when(tasks.findById(11L)).thenReturn(Optional.of(task()));
        when(instances.findById(10L)).thenReturn(Optional.of(instance()));
        when(security.principal()).thenReturn(principal);
    }

    private WorkflowTask task() {
        return new WorkflowTask(11L, "WFT-11", 10L, 2L, 3L, 12L,
                "FIRST", "First", 1, "FIRST:1:1", 20L, "{\"userId\":20}",
                WorkflowTask.Status.PENDING, "APPROVE,REJECT", null, null,
                null, null, null, 0);
    }

    private AssignmentSnapshot snapshot() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 10, 0);
        AssignmentContext context = new AssignmentContext(
                11L, 10L, 2L, 3L, 12L, 100L, 20L, 30L, "20", now, "trace");
        return AssignmentSnapshot.create(13L, context,
                new ExplicitUserAssignmentStrategy().resolve(context, 20L));
    }

    private WorkflowInstance instance() {
        LocalDateTime now = LocalDateTime.now();
        WorkflowDefinition definition = new WorkflowDefinition(1L, "TEST", "Test", "TEST",
                100L, 30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 0);
        WorkflowVersion version = new WorkflowVersion(2L, 1L, 1,
                WorkflowVersion.Status.PUBLISHED, "1.0", "hash", null, now, null,
                20L, now, null, 0);
        return WorkflowInstance.running(10L, "WFI-10", definition, version, "TEST", "1",
                "TEST:1", 100L, null, null, 1, 20L, 30L, 3L,
                null, "start", "request", null, now);
    }
}
