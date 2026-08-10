package cn.gov.enterprise.modules.project.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import cn.gov.enterprise.modules.project.domain.repository.ProjectRepository;
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import org.springframework.stereotype.Service;

/** Shared authorization entry for project child resources. */
@Service
public class ProjectResourceAccessService {
    private final ProjectAccessPolicy accessPolicy;
    private final ProjectRepository projectRepository;

    public ProjectResourceAccessService(
            ProjectAccessPolicy accessPolicy, ProjectRepository projectRepository) {
        this.accessPolicy = accessPolicy;
        this.projectRepository = projectRepository;
    }

    public ProjectAggregate requireAccessible(Long projectId) {
        accessPolicy.requireAccessible(projectId);
        ProjectAggregate project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("B0404", "项目不存在"));
        accessPolicy.requireAccessible(project);
        return project;
    }
}
