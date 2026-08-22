package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import java.util.List;
import java.util.Optional;

public interface WorkflowTransitionRepository {
    Optional<WorkflowTransition> findById(Long id);
    List<WorkflowTransition> findByVersionId(Long versionId);
    List<WorkflowTransition> findOutgoing(
            Long versionId, Long fromNodeId, WorkflowTransition.TriggerType triggerType);
    void save(WorkflowTransition transition);
}
