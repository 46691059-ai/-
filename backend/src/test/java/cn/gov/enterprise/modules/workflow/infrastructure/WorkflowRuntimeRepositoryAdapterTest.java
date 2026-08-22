package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowInstanceRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowPersistenceAudit;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowTaskRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowInstanceMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowRuntimeRepositoryAdapterTest {
    @Test
    void instanceAdapterShouldPersistRuntimeSnapshots() {
        WorkflowInstanceMapper mapper = org.mockito.Mockito.mock(WorkflowInstanceMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowInstanceEntity.class))).thenReturn(1);
        WorkflowInstanceRepositoryImpl repository = new WorkflowInstanceRepositoryImpl(mapper, audit);

        repository.save(instance());

        ArgumentCaptor<WorkflowInstanceEntity> captor = ArgumentCaptor.forClass(WorkflowInstanceEntity.class);
        verify(mapper).insert(captor.capture());
        verify(audit).initialize(captor.getValue());
        assertThat(captor.getValue().getStatus()).isEqualTo("RUNNING");
        assertThat(captor.getValue().getDefinitionCodeSnapshot()).isEqualTo("CONTRACT_APPROVAL");
        assertThat(captor.getValue().getDefinitionContentHashSnapshot()).isEqualTo("hash");
    }

    @Test
    void taskAdapterShouldPersistPendingTask() {
        WorkflowTaskMapper mapper = org.mockito.Mockito.mock(WorkflowTaskMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowTaskEntity.class))).thenReturn(1);
        WorkflowTaskRepositoryImpl repository = new WorkflowTaskRepositoryImpl(mapper, audit);
        WorkflowTask task = new WorkflowTask(11L, "WFT-11", 10L, 2L, 3L, "FIRST", "首节点",
                1, "FIRST:1", null, "{}", WorkflowTask.Status.PENDING,
                "CLAIM,APPROVE,REJECT", null, null, null, null, null, 0);

        repository.save(task);

        verify(mapper).insert(any(WorkflowTaskEntity.class));
    }

    private WorkflowInstance instance() {
        LocalDateTime now = LocalDateTime.now();
        WorkflowDefinition definition = new WorkflowDefinition(1L, "CONTRACT_APPROVAL", "合同审批",
                "CONTRACT", 100L, 30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 1);
        WorkflowVersion workflowVersion = new WorkflowVersion(2L, 1L, 1,
                WorkflowVersion.Status.PUBLISHED, "1.0", "hash", null, now, null, 20L, now, null, 1);
        return WorkflowInstance.running(10L, "WFI-10", definition, workflowVersion,
                "CONTRACT", "B-1", "CONTRACT:B-1", 100L, null, null, 1,
                20L, 30L, 3L, null, "idem-1", "request-hash", null, now);
    }
}
