package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBindingSet;
import java.util.Optional;

public interface ResolverBindingSetRepository {
    void save(WorkflowResolverBindingSet bindingSet);
    Optional<WorkflowResolverBindingSet> findByInstanceId(Long instanceId);
}
