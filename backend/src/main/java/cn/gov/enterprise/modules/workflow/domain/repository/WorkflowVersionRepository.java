package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import java.util.List;
import java.util.Optional;

public interface WorkflowVersionRepository {
    Optional<WorkflowVersion> findById(Long id);
    Optional<WorkflowVersion> findByIdForUpdate(Long id);
    List<WorkflowVersion> findByDefinitionId(Long definitionId);
    int nextVersionNo(Long definitionId);
    void save(WorkflowVersion version);
    boolean updateState(WorkflowVersion version, WorkflowVersion.Status expectedStatus, int expectedVersion);
}
