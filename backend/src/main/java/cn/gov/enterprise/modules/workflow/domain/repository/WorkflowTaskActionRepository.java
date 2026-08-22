package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTaskAction;
import java.util.Optional;

public interface WorkflowTaskActionRepository {
    Optional<WorkflowTaskAction> findByTaskIdAndIdempotencyKey(Long taskId, String idempotencyKey);
    void save(WorkflowTaskAction action);
}
