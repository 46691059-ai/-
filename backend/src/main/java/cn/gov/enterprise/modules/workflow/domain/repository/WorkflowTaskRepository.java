package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import java.util.List;
import java.util.Optional;

public interface WorkflowTaskRepository {
    Optional<WorkflowTask> findById(Long id);
    Optional<WorkflowTask> findByIdForUpdate(Long id);
    Optional<WorkflowTask> findByNodeExecutionId(Long nodeExecutionId);
    List<WorkflowTask> findByInstanceId(Long instanceId);
    void save(WorkflowTask task);
    void update(WorkflowTask task);
    boolean claim(WorkflowTask task, int expectedVersion);
}
