package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import java.util.Optional;

public interface WorkflowDefinitionRepository {
    boolean existsByEnterpriseIdAndCode(Long enterpriseId, String definitionCode);
    Optional<WorkflowDefinition> findById(Long id);
    Optional<WorkflowDefinition> findByIdForUpdate(Long id);
    void save(WorkflowDefinition definition);
    boolean updateCurrentVersion(WorkflowDefinition definition, int expectedVersion);
}
