package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowNodeExecutionRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowPersistenceAudit;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowTransitionRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeExecutionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTransitionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowNodeExecutionMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTransitionMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class WorkflowMultiNodeRepositoryAdapterTest {
    @Test
    void transitionAdapterMustPersistFrozenRouteFields() {
        WorkflowTransitionMapper mapper = org.mockito.Mockito.mock(WorkflowTransitionMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowTransitionEntity.class))).thenReturn(1);
        WorkflowTransitionRepositoryImpl repository = new WorkflowTransitionRepositoryImpl(mapper, audit);

        repository.save(transition());

        ArgumentCaptor<WorkflowTransitionEntity> captor = ArgumentCaptor.forClass(WorkflowTransitionEntity.class);
        verify(mapper).insert(captor.capture());
        verify(audit).initialize(captor.getValue());
        assertThat(captor.getValue().getTriggerType()).isEqualTo("APPROVE");
        assertThat(captor.getValue().getRouteType()).isEqualTo("DIRECT");
    }

    @Test
    void transitionAdapterMustTranslateUniqueKeyConflict() {
        WorkflowTransitionMapper mapper = org.mockito.Mockito.mock(WorkflowTransitionMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowTransitionEntity.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        WorkflowTransitionRepositoryImpl repository = new WorkflowTransitionRepositoryImpl(mapper, audit);

        assertThatThrownBy(() -> repository.save(transition()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("duplicated");
    }

    @Test
    void nodeExecutionAdapterMustUseOptimisticStateUpdate() {
        WorkflowNodeExecutionMapper mapper = org.mockito.Mockito.mock(WorkflowNodeExecutionMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(audit.operator()).thenReturn("operator");
        when(mapper.updateState(any(WorkflowNodeExecutionEntity.class),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq("operator")))
                .thenReturn(1);
        WorkflowNodeExecutionRepositoryImpl repository = new WorkflowNodeExecutionRepositoryImpl(mapper, audit);
        LocalDateTime now = LocalDateTime.now();
        WorkflowNodeExecution execution = new WorkflowNodeExecution(20L, "WFNE-20", 10L, 2L, 3L,
                "REVIEW", "Review", 1, null, null, WorkflowNodeExecution.Status.ACTIVE,
                null, now, now, null, null, null, "trace", 0);

        assertThat(repository.updateState(execution, 0)).isTrue();
        verify(mapper).updateState(any(WorkflowNodeExecutionEntity.class),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq("operator"));
    }

    private WorkflowTransition transition() {
        return new WorkflowTransition(1L, 2L, "T-1", "Next", 3L, 4L,
                WorkflowTransition.TriggerType.APPROVE, WorkflowTransition.RouteType.DIRECT,
                1, null, true, 0);
    }
}
