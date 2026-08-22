package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import java.util.List;
import java.util.Optional;

public interface ResolverBindingRepository {
    void saveAll(List<WorkflowResolverBinding> bindings);
    List<WorkflowResolverBinding> findByInstanceId(Long instanceId);
    Optional<WorkflowResolverBinding> findById(Long id);
}
