package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import java.util.List;
import java.util.Optional;

public interface VersionNodeResolverBindingRepository {
    void add(VersionNodeResolverBinding binding);
    void update(VersionNodeResolverBinding binding);
    boolean logicalDelete(Long id, int expectedVersion);
    Optional<VersionNodeResolverBinding> findById(Long id);
    List<VersionNodeResolverBinding> findByVersionId(Long definitionVersionId);
    List<VersionNodeResolverBinding> findByVersionIdAndNodeId(
            Long definitionVersionId, Long nodeId);
}
