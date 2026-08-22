package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingLifecycleEvent;
import java.util.List;
import java.util.Optional;

public interface RoleRuntimeBindingLifecycleRepository {
    void append(RoleRuntimeBindingLifecycleEvent event);
    Optional<RoleRuntimeBindingLifecycleEvent> findLatest(String snapshotId);
    List<RoleRuntimeBindingLifecycleEvent> list(String snapshotId);
}
