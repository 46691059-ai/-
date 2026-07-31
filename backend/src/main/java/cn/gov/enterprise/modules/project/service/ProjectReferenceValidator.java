package cn.gov.enterprise.modules.project.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.mapper.ProjectReferenceMapper;
import org.springframework.stereotype.Component;

@Component
public class ProjectReferenceValidator {
    private final ProjectReferenceMapper mapper;

    public ProjectReferenceValidator(ProjectReferenceMapper mapper) {
        this.mapper = mapper;
    }

    public void requireActiveOrgAndManager(Long orgId, Long managerUserId) {
        if (mapper.countActiveOrg(orgId) == 0) {
            throw new BusinessException("B0001", "所属组织不存在或已停用");
        }
        if (mapper.countActiveUserInOrg(managerUserId, orgId) == 0) {
            throw new BusinessException("B0001", "项目负责人不存在、已停用或不属于项目组织");
        }
    }

    public void requireActiveUser(Long userId, String label) {
        if (userId != null && mapper.countActiveUser(userId) == 0) {
            throw new BusinessException("B0001", label + "不存在或已停用");
        }
    }
}
