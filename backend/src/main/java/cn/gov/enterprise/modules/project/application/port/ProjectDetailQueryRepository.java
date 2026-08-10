package cn.gov.enterprise.modules.project.application.port;

import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;

/** Read-side repository used to assemble the existing project detail response. */
public interface ProjectDetailQueryRepository {
    ProjectDtos.DetailResponse findDetail(ProjectAggregate project);
}
