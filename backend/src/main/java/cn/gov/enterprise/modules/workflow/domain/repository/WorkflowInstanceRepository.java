package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import java.util.Optional;

public interface WorkflowInstanceRepository {
    Optional<WorkflowInstance> findById(Long id);
    Optional<WorkflowInstance> findByIdForUpdate(Long id);
    Optional<WorkflowInstance> findByEnterpriseIdAndIdempotencyKey(Long enterpriseId, String idempotencyKey);
    void save(WorkflowInstance instance);
    void update(WorkflowInstance instance);
}
