package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import java.util.Optional;

public interface WorkflowVersionReleaseRepository {
    void save(WorkflowVersionRelease release);
    Optional<WorkflowVersionRelease> findByDefinitionIdAndPublishedVersionId(
            Long definitionId, Long publishedVersionId);
}
