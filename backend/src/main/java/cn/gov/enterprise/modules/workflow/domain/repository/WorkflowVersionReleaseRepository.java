package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;

public interface WorkflowVersionReleaseRepository {
    void save(WorkflowVersionRelease release);
}
