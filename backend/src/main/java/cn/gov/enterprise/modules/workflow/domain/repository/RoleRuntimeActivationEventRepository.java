package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.canary.CanaryScope;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationEvent;
import java.util.Optional;

public interface RoleRuntimeActivationEventRepository {
    Optional<RoleRuntimeActivationEvent> findByExactScope(CanaryScope scope);
    long countByExactScope(CanaryScope scope);
    boolean append(RoleRuntimeActivationEvent event);
}
