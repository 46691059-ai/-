package cn.gov.enterprise.modules.system.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.system.entity.SysOrgEntity;
import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import cn.gov.enterprise.modules.system.mapper.SysOrgMapper;
import cn.gov.enterprise.modules.system.mapper.SysRoleMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserRoleMapper;
import cn.gov.enterprise.modules.system.user.dto.UserCreateRequest;
import cn.gov.enterprise.modules.system.user.dto.UserStatusRequest;
import cn.gov.enterprise.modules.system.user.mapper.UserManagementMapper;
import cn.gov.enterprise.modules.system.user.service.impl.UserManagementServiceImpl;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityIdentityService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {
    @Mock SysUserMapper userMapper;
    @Mock SysRoleMapper roleMapper;
    @Mock SysUserRoleMapper userRoleMapper;
    @Mock SysOrgMapper orgMapper;
    @Mock UserManagementMapper managementMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CurrentSecurityContext securityContext;
    @Mock SecurityIdentityService identityService;
    private UserManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserManagementServiceImpl(userMapper, roleMapper, userRoleMapper, orgMapper,
                managementMapper, passwordEncoder, securityContext, identityService);
    }

    @Test
    void createEncryptsPasswordAndInitializesSecurityState() {
        SysOrgEntity org = new SysOrgEntity();
        org.setStatus(1);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(orgMapper.selectById(100L)).thenReturn(org);
        when(passwordEncoder.encode("Secure123")).thenReturn("$2a$12$encoded");

        service.create(new UserCreateRequest("tester", "Secure123", "测试用户", "", "", 100L, 1));

        ArgumentCaptor<SysUserEntity> captor = ArgumentCaptor.forClass(SysUserEntity.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$12$encoded");
        assertThat(captor.getValue().getTokenVersion()).isZero();
        assertThat(captor.getValue().getLoginFailCount()).isZero();
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.create(
                new UserCreateRequest("tester", "Secure123", "测试用户", null, null, 100L, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名已存在");
    }

    @Test
    void deleteRejectsCurrentUser() {
        when(securityContext.userId()).thenReturn(10L);
        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("禁止删除当前登录用户");
    }

    @Test
    void regularAdminCannotDeleteSuperAdmin() {
        SysUserEntity target = new SysUserEntity();
        target.setId(2L);
        when(securityContext.userId()).thenReturn(1L);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(managementMapper.countSuperAdminRole(2L)).thenReturn(1L);
        when(managementMapper.countSuperAdminRole(1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("普通管理员不能操作系统管理员");
    }

    @Test
    void statusChangeInvalidatesExistingToken() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2L);
        user.setVersion(3);
        user.setTokenVersion(4);
        when(userMapper.selectById(2L)).thenReturn(user);
        when(userMapper.updateById(user)).thenReturn(1);

        service.changeStatus(new UserStatusRequest(2L, 0, 3));

        assertThat(user.getStatus()).isZero();
        assertThat(user.getTokenVersion()).isEqualTo(5);
        verify(identityService).evict(2L);
    }
}
