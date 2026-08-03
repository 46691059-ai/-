package cn.gov.enterprise.modules.system.user.service;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.system.user.dto.*;
import cn.gov.enterprise.modules.system.user.vo.RoleOptionVO;
import cn.gov.enterprise.modules.system.user.vo.OrgOptionVO;
import cn.gov.enterprise.modules.system.user.vo.UserVO;
import java.util.List;

public interface UserManagementService {
    PageResponse<UserVO> page(UserPageQuery query);
    UserVO detail(Long id);
    Long create(UserCreateRequest request);
    void update(UserUpdateRequest request);
    void delete(Long id);
    void changeStatus(UserStatusRequest request);
    void resetPassword(UserResetPasswordRequest request);
    void assignRoles(UserRoleAssignRequest request);
    List<RoleOptionVO> roleOptions();
    List<OrgOptionVO> orgOptions();
}
