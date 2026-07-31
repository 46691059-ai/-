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

    public void requireActiveOrgAndLeader(Long orgId, Long leaderId) {
        if (mapper.countActiveOrg(orgId) == 0) {
            throw new BusinessException("B0001", "所属组织不存在或已停用");
        }
        if (mapper.countActiveEmployeeInOrg(leaderId, orgId) == 0) {
            throw new BusinessException("B0001", "项目负责人不存在、已停用或不属于项目组织");
        }
    }

    public void requireActiveEmployee(Long employeeId, String label) {
        if (employeeId != null && mapper.countActiveEmployee(employeeId) == 0) {
            throw new BusinessException("B0001", label + "不存在或已停用");
        }
    }
}
