package cn.gov.enterprise.modules.system.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.system.entity.SysOrgEntity;
import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import cn.gov.enterprise.modules.system.mapper.SysOrgMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import cn.gov.enterprise.modules.system.profile.service.impl.ProfileServiceImpl;
import cn.gov.enterprise.modules.system.user.mapper.UserManagementMapper;
import cn.gov.enterprise.security.CurrentSecurityContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {
    @Mock SysUserMapper userMapper;
    @Mock SysOrgMapper orgMapper;
    @Mock UserManagementMapper userManagementMapper;
    @Mock CurrentSecurityContext securityContext;
    private ProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProfileServiceImpl(userMapper, orgMapper, userManagementMapper, securityContext);
    }

    @Test
    void returnsOnlyCurrentUsersBasicProfileAndRoles() {
        SysUserEntity user = new SysUserEntity();
        user.setId(90002L);
        user.setUsername("employee_test");
        user.setRealName("普通员工测试");
        user.setPhone("13800000002");
        user.setOrgId(10301L);
        SysOrgEntity org = new SysOrgEntity();
        org.setId(10301L);
        org.setOrgName("数据标注团队");
        when(securityContext.userId()).thenReturn(90002L);
        when(userMapper.selectById(90002L)).thenReturn(user);
        when(orgMapper.selectById(10301L)).thenReturn(org);
        when(userManagementMapper.selectUserRoles(List.of(90002L))).thenReturn(List.of(
                new UserManagementMapper.UserRoleRow(90002L, 7L, "普通员工", "COMMON_USER")));

        var profile = service.currentProfile();

        assertThat(profile.username()).isEqualTo("employee_test");
        assertThat(profile.orgName()).isEqualTo("数据标注团队");
        assertThat(profile.roleCodes()).containsExactly("COMMON_USER");
    }
}
