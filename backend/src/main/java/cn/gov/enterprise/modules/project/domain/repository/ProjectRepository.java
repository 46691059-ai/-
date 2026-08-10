package cn.gov.enterprise.modules.project.domain.repository;

import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import java.util.Optional;

/**
 * Persistence-agnostic repository port for the Project aggregate.
 *
 * <p>No implementation replaces the current MyBatis mappers in this sprint.</p>
 */
public interface ProjectRepository {
    Optional<ProjectAggregate> findById(Long projectId);

    boolean existsByProjectNo(String projectNo);

    void save(ProjectAggregate project);
}
