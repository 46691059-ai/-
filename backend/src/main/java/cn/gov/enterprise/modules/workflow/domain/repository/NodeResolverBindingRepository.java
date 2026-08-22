package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import java.util.List;
import java.util.Optional;

public interface NodeResolverBindingRepository {
    void saveAll(List<NodeResolverBinding> bindings);
    List<NodeResolverBinding> findByInstanceId(Long instanceId);
    Optional<NodeResolverBinding> findByInstanceIdAndNodeId(Long instanceId, Long nodeId);
}
