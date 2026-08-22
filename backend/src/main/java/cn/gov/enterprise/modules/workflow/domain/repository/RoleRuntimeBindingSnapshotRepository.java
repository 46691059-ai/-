package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeBindingSnapshot;
import java.util.Optional;

/** Insert/query-only port. Runtime Binding snapshots are never updated in place. */
public interface RoleRuntimeBindingSnapshotRepository {
    void insert(RoleRuntimeBindingSnapshot snapshot);
    Optional<RoleRuntimeBindingSnapshot> findById(Long id);
    Optional<RoleRuntimeBindingSnapshot> findByInstanceIdAndNodeId(Long instanceId, Long nodeId);
}
