package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTaskAction;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowPersistenceAudit;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowTaskActionRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowTaskRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskActionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskActionMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowTaskActionRepositoryTest {
    @Test
    void actionAdapterShouldPersistImmutableActionEvidence() {
        WorkflowTaskActionMapper mapper = org.mockito.Mockito.mock(WorkflowTaskActionMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowTaskActionEntity.class))).thenReturn(1);
        WorkflowTaskActionRepositoryImpl repository = new WorkflowTaskActionRepositoryImpl(mapper, audit);
        WorkflowTaskAction action = action();

        repository.save(action);

        ArgumentCaptor<WorkflowTaskActionEntity> captor =
                ArgumentCaptor.forClass(WorkflowTaskActionEntity.class);
        verify(mapper).insert(captor.capture());
        verify(audit).initialize(captor.getValue());
        assertThat(captor.getValue().getActionType()).isEqualTo("APPROVE");
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("action-1");
    }

    @Test
    void optimisticLockConflictMustRejectConcurrentTaskProcessing() {
        WorkflowTaskMapper mapper = org.mockito.Mockito.mock(WorkflowTaskMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.updateById(any(WorkflowTaskEntity.class))).thenReturn(0);
        WorkflowTaskRepositoryImpl repository = new WorkflowTaskRepositoryImpl(mapper, audit);

        assertThatThrownBy(() -> repository.update(task().approve(20L, LocalDateTime.now())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("concurrently");
        verify(audit).touch(any(WorkflowTaskEntity.class));
    }

    private WorkflowTaskAction action() {
        return WorkflowTaskAction.create(12L, "WFA-12", task(),
                WorkflowTaskAction.ActionType.APPROVE, 20L, 30L, "同意",
                LocalDateTime.now(), "action-1", "request-hash", "trace-1");
    }

    private WorkflowTask task() {
        return new WorkflowTask(11L, "WFT-11", 10L, 2L, 3L, "FIRST", "首节点",
                1, "FIRST:1", null, "{}", WorkflowTask.Status.PENDING,
                "APPROVE,REJECT", null, null, null, null, null, 0);
    }
}
