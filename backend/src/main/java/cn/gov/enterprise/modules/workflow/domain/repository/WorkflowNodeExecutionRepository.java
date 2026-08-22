package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import java.util.List;
import java.util.Optional;

public interface WorkflowNodeExecutionRepository {
    Optional<WorkflowNodeExecution> findById(Long id);
    Optional<WorkflowNodeExecution> findByIdForUpdate(Long id);
    Optional<WorkflowNodeExecution> findByInstanceIdAndNodeIdAndVisitNo(
            Long instanceId, Long nodeId, int visitNo);
    List<WorkflowNodeExecution> findByInstanceId(Long instanceId);
    void save(WorkflowNodeExecution execution);
    boolean updateState(WorkflowNodeExecution execution, int expectedVersion);
}
