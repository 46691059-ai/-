package cn.gov.enterprise.modules.project.domain.repository;

import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleTemplateDefinition;
import java.util.Optional;

/** Selects the single active lifecycle template version for a new project. */
public interface LifecycleTemplateRepository {
    Optional<LifecycleTemplateDefinition> findActive(String projectType, Long orgId);
}
