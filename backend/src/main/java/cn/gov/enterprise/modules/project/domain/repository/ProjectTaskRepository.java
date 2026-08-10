package cn.gov.enterprise.modules.project.domain.repository;

import cn.gov.enterprise.modules.project.domain.model.task.ProjectTask;
import java.util.List;
import java.util.Optional;

/** Persistence port for project task sub-aggregates. */
public interface ProjectTaskRepository {
    List<ProjectTask> findPage(Long projectId, Long stageId, long offset, long limit);
    long count(Long projectId, Long stageId);
    Optional<ProjectTask> findById(Long projectId, Long taskId);
    boolean existsTaskNo(Long projectId, String taskNo, Long excludedTaskId);
    void insert(ProjectTask task);
    void update(ProjectTask task);
}
