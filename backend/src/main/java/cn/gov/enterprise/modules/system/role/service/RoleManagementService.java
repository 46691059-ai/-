package cn.gov.enterprise.modules.system.role.service;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.system.role.dto.RoleCreateRequest;
import cn.gov.enterprise.modules.system.role.dto.RoleMenuAssignRequest;
import cn.gov.enterprise.modules.system.role.dto.RolePageQuery;
import cn.gov.enterprise.modules.system.role.dto.RoleUpdateRequest;
import cn.gov.enterprise.modules.system.role.vo.MenuTreeVO;
import cn.gov.enterprise.modules.system.role.vo.RolePermissionVO;
import cn.gov.enterprise.modules.system.role.vo.RoleVO;
import java.util.List;

public interface RoleManagementService {
    PageResponse<RoleVO> page(RolePageQuery query);
    RoleVO detail(Long id);
    Long create(RoleCreateRequest request);
    void update(RoleUpdateRequest request);
    void delete(Long id);
    List<MenuTreeVO> menuTree();
    RolePermissionVO rolePermissions(Long roleId);
    void assignMenus(RoleMenuAssignRequest request);
}
