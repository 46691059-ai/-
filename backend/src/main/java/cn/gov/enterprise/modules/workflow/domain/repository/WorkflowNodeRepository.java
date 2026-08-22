package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import java.util.List;
import java.util.Optional;

public interface WorkflowNodeRepository {
    Optional<WorkflowNode> findById(Long id);
    List<WorkflowNode> findByVersionId(Long versionId);
    List<WorkflowNode> findByVersionIds(List<Long> versionIds);
    void replaceNodes(Long versionId, List<WorkflowNode> nodes);
}
