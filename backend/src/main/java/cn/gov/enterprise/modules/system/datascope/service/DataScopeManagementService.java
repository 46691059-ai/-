package cn.gov.enterprise.modules.system.datascope.service;

import cn.gov.enterprise.modules.system.datascope.dto.RoleDataScopeSaveRequest;
import cn.gov.enterprise.modules.system.datascope.vo.RoleDataScopeVO;
import cn.gov.enterprise.modules.system.org.vo.OrgTreeVO;
import java.util.List;

public interface DataScopeManagementService {
    RoleDataScopeVO roleDataScope(Long roleId);

    void saveRoleDataScope(RoleDataScopeSaveRequest request);

    List<OrgTreeVO> selectableOrganizationTree();
}
