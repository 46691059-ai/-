package cn.gov.enterprise.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import cn.gov.enterprise.modules.system.log.service.AuditLogService;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {
    @Mock SysUserMapper userMapper;
    @Mock SecurityIdentityMapper identityMapper;
    @Mock JwtTokenService tokenService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SecurityIdentityService identityService;
    @Mock PermissionCacheService permissionCacheService;
    @Mock AuditLogService auditLogService;
    private LoginService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(any())).thenReturn("dummy-hash");
        org.mockito.Mockito.lenient().when(identityService.load(any())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return new SecuritySnapshot(userId, "test", 100L, 0,
                    Set.copyOf(identityMapper.selectPermissions(userId)), Set.of(), true, false);
        });
        org.mockito.Mockito.lenient().when(permissionCacheService.roles(any(), any())).thenAnswer(invocation ->
                ((Supplier<List<String>>) invocation.getArgument(1)).get());
        org.mockito.Mockito.lenient().when(permissionCacheService.menus(any(), any())).thenAnswer(invocation ->
                ((Supplier<List<SecurityIdentityMapper.SecurityMenuRow>>) invocation.getArgument(1)).get());
        service = new LoginService(userMapper, identityMapper, tokenService, passwordEncoder,
                identityService, permissionCacheService, auditLogService);
    }

    @Test
    void successfulLoginReturnsRolesMenusAndAuthorities() {
        SysUserEntity user = user(1L, 1);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Secure123", "hash")).thenReturn(true);
        when(identityMapper.selectRoleCodes(1L)).thenReturn(List.of("SUPER_ADMIN"));
        when(identityMapper.selectPermissions(1L)).thenReturn(List.of("user:add", "system:user:view"));
        when(identityMapper.selectMenus(1L)).thenReturn(List.of(
                new SecurityIdentityMapper.SecurityMenuRow(900L, null, "系统管理", "M", "/system", null,
                        "system:view", "Setting", 1, 1),
                new SecurityIdentityMapper.SecurityMenuRow(911L, 900L, "用户新增", "B", null, null,
                        "user:add", null, 1, 0)));
        when(tokenService.createToken(1L, 0)).thenReturn("jwt-token");

        LoginResponse response = service.login(new LoginRequest("admin", "Secure123"), "127.0.0.1");

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.roles()).containsExactly("SUPER_ADMIN");
        assertThat(response.permissions()).containsExactly("system:user:view", "user:add");
        assertThat(response.buttonPermissions()).containsExactly("user:add");
        assertThat(response.menus()).extracting(LoginResponse.MenuItem::menuName).containsExactly("系统管理");
        verify(auditLogService).save(argThat(command ->
                "LOGIN_SUCCESS".equals(command.logType()) && command.requestParams() == null));
    }

    @Test
    void disabledUserCannotLogin() {
        when(userMapper.selectOne(any())).thenReturn(user(2L, 0));
        assertThatThrownBy(() -> service.login(new LoginRequest("disabled", "Secure123"), "127.0.0.1"))
                .isInstanceOf(DisabledException.class);
        verify(auditLogService).save(argThat(command ->
                "LOGIN_FAIL".equals(command.logType()) && "账号停用".equals(command.errorMessage())));
    }

    @Test
    void projectManagerLoginReceivesProjectMenuAndOnlyGrantedButtons() {
        SysUserEntity user = user(90001L, 1);
        user.setUsername("project_manager");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Secure123", "hash")).thenReturn(true);
        when(identityMapper.selectRoleCodes(90001L)).thenReturn(List.of("PROJECT_MANAGER"));
        when(identityMapper.selectPermissions(90001L)).thenReturn(List.of(
                "project:view", "project:lifecycle:list", "project:add", "project:edit"));
        when(identityMapper.selectMenus(90001L)).thenReturn(List.of(
                new SecurityIdentityMapper.SecurityMenuRow(400L, null, "项目管理", "M", "/projects", null,
                        "project:view", "FolderOpened", 1, 1),
                new SecurityIdentityMapper.SecurityMenuRow(410L, 400L, "项目列表", "C", "/projects", "projects/index",
                        "project:lifecycle:list", null, 1, 1),
                new SecurityIdentityMapper.SecurityMenuRow(411L, 410L, "项目新增", "B", null, null,
                        "project:add", null, 1, 0),
                new SecurityIdentityMapper.SecurityMenuRow(412L, 410L, "项目修改", "B", null, null,
                        "project:edit", null, 2, 0)));
        when(tokenService.createToken(90001L, 0)).thenReturn("project-token");

        LoginResponse response = service.login(new LoginRequest("project_manager", "Secure123"), "127.0.0.1");

        assertThat(response.menus()).extracting(LoginResponse.MenuItem::menuName).containsExactly("项目管理");
        assertThat(response.buttonPermissions()).containsExactly("project:add", "project:edit");
        assertThat(response.permissions()).doesNotContain("investment:view", "data-asset:view", "system:view");
    }

    @Test
    void commonEmployeeLoginOnlyReceivesProfileMenu() {
        SysUserEntity user = user(90002L, 1);
        user.setUsername("employee_test");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Secure123", "hash")).thenReturn(true);
        when(identityMapper.selectRoleCodes(90002L)).thenReturn(List.of("COMMON_USER"));
        when(identityMapper.selectPermissions(90002L)).thenReturn(List.of("profile:view"));
        when(identityMapper.selectMenus(90002L)).thenReturn(List.of(
                new SecurityIdentityMapper.SecurityMenuRow(950L, null, "个人中心", "C", "/profile", "profile/index",
                        "profile:view", "UserFilled", 1, 10)));
        when(tokenService.createToken(90002L, 0)).thenReturn("employee-token");

        LoginResponse response = service.login(new LoginRequest("employee_test", "Secure123"), "127.0.0.1");

        assertThat(response.menus()).extracting(LoginResponse.MenuItem::menuName).containsExactly("个人中心");
        assertThat(response.permissions()).containsExactly("profile:view");
        assertThat(response.buttonPermissions()).isEmpty();
    }

    private SysUserEntity user(Long id, int status) {
        SysUserEntity user = new SysUserEntity(); user.setId(id); user.setUsername("admin");
        user.setRealName("管理员"); user.setPassword("hash"); user.setOrgId(100L);
        user.setStatus(status); user.setTokenVersion(0); user.setLoginFailCount(0); return user;
    }
}
