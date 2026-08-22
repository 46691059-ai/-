package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowPersistenceAudit;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowVersionReleaseRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionReleaseEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowVersionReleaseMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowVersionReleaseRepositoryTest {
    @Test
    void shouldMapImmutableReleaseFact() {
        WorkflowVersionReleaseMapper mapper = org.mockito.Mockito.mock(WorkflowVersionReleaseMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowVersionReleaseEntity.class))).thenReturn(1);
        var repository = new WorkflowVersionReleaseRepositoryImpl(mapper, audit);
        var release = new WorkflowVersionRelease(1L, 2L, 3L, 4L, 2, "a".repeat(64),
                5L, 6L, LocalDateTime.now(), "trace", "validated");

        repository.save(release);

        ArgumentCaptor<WorkflowVersionReleaseEntity> captor = ArgumentCaptor.forClass(WorkflowVersionReleaseEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getPreviousVersionId()).isEqualTo(3L);
        assertThat(captor.getValue().getPublishedVersionId()).isEqualTo(4L);
        assertThat(captor.getValue().getContentHash()).isEqualTo("a".repeat(64));
        verify(audit).initialize(captor.getValue());
    }
}
