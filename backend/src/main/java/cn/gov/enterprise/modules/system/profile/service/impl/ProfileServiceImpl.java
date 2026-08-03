package cn.gov.enterprise.modules.system.profile.service.impl;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.system.entity.SysOrgEntity;
import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import cn.gov.enterprise.modules.system.mapper.SysOrgMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import cn.gov.enterprise.modules.system.profile.service.ProfileService;
import cn.gov.enterprise.modules.system.profile.vo.ProfileVO;
import cn.gov.enterprise.modules.system.user.mapper.UserManagementMapper;
import cn.gov.enterprise.security.CurrentSecurityContext;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProfileServiceImpl implements ProfileService {
    private final SysUserMapper userMapper;
    private final SysOrgMapper orgMapper;
    private final UserManagementMapper userManagementMapper;
    private final CurrentSecurityContext securityContext;

    public ProfileServiceImpl(
            SysUserMapper userMapper,
            SysOrgMapper orgMapper,
            UserManagementMapper userManagementMapper,
            CurrentSecurityContext securityContext) {
        this.userMapper = userMapper;
        this.orgMapper = orgMapper;
        this.userManagementMapper = userManagementMapper;
        this.securityContext = securityContext;
    }

    @Override
    public ProfileVO currentProfile() {
        Long userId = securityContext.userId();
        SysUserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("B0404", "当前用户不存在");
        }
        SysOrgEntity org = orgMapper.selectById(user.getOrgId());
        List<UserManagementMapper.UserRoleRow> roles = userManagementMapper.selectUserRoles(List.of(userId));
        return new ProfileVO(user.getId(), user.getUsername(), user.getRealName(), user.getPhone(),
                user.getOrgId(), org == null ? null : org.getOrgName(),
                roles.stream().map(UserManagementMapper.UserRoleRow::roleCode).toList(),
                roles.stream().map(UserManagementMapper.UserRoleRow::roleName).toList());
    }
}
