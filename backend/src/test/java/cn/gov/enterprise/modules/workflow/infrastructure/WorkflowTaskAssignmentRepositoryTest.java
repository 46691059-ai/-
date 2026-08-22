package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentContext;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserAssignmentStrategy;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowPersistenceAudit;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowTaskAssignmentSnapshotRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskAssignmentSnapshotEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskAssignmentSnapshotMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class WorkflowTaskAssignmentRepositoryTest {
    @Test
    void adapterMustPersistCanonicalResolvedUsers() {
        WorkflowTaskAssignmentSnapshotMapper mapper =
                org.mockito.Mockito.mock(WorkflowTaskAssignmentSnapshotMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowTaskAssignmentSnapshotEntity.class))).thenReturn(1);
        WorkflowTaskAssignmentSnapshotRepositoryImpl repository =
                new WorkflowTaskAssignmentSnapshotRepositoryImpl(mapper, audit);

        repository.save(snapshot());

        ArgumentCaptor<WorkflowTaskAssignmentSnapshotEntity> captor =
                ArgumentCaptor.forClass(WorkflowTaskAssignmentSnapshotEntity.class);
        verify(mapper).insert(captor.capture());
        verify(audit).initialize(captor.getValue());
        assertThat(captor.getValue().getStrategyType()).isEqualTo("USER");
        assertThat(captor.getValue().getResolvedUsers()).isEqualTo("[99]");
        assertThat(captor.getValue().getResolvedUserCount()).isEqualTo(1);
    }

    @Test
    void duplicateSnapshotMustBeTranslated() {
        WorkflowTaskAssignmentSnapshotMapper mapper =
                org.mockito.Mockito.mock(WorkflowTaskAssignmentSnapshotMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowTaskAssignmentSnapshotEntity.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        WorkflowTaskAssignmentSnapshotRepositoryImpl repository =
                new WorkflowTaskAssignmentSnapshotRepositoryImpl(mapper, audit);

        assertThatThrownBy(() -> repository.save(snapshot()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    private AssignmentSnapshot snapshot() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 10, 0);
        AssignmentContext context = new AssignmentContext(
                11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L,
                "17", now, "trace-1");
        return AssignmentSnapshot.create(20L, context,
                new ExplicitUserAssignmentStrategy().resolve(context, 99L));
    }
}
