package cn.gov.enterprise.security;

import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import cn.gov.enterprise.modules.system.log.service.AuditLogCommand;
import cn.gov.enterprise.modules.system.log.service.AuditLogService;
import cn.gov.enterprise.modules.system.log.support.AuditRequestSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {
    private static final Logger log = LoggerFactory.getLogger(LoginService.class);
    private static final int MAX_FAILURES = 5;
    private static final int LOCK_MINUTES = 15;

    private final SysUserMapper userMapper;
    private final SecurityIdentityMapper identityMapper;
    private final JwtTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityIdentityService identityService;
    private final PermissionCacheService permissionCacheService;
    private final AuditLogService auditLogService;
    private final String dummyPasswordHash;

    public LoginService(
            SysUserMapper userMapper,
            SecurityIdentityMapper identityMapper,
            JwtTokenService tokenService,
            PasswordEncoder passwordEncoder,
            SecurityIdentityService identityService,
            PermissionCacheService permissionCacheService,
            AuditLogService auditLogService) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.identityService = identityService;
        this.permissionCacheService = permissionCacheService;
        this.auditLogService = auditLogService;
        this.dummyPasswordHash = passwordEncoder.encode("enterprise-platform-dummy-password");
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponse login(LoginRequest request, String clientIp) {
        long started = System.nanoTime();
        String username = request.username().trim();
        SysUserEntity user = userMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getUsername, username));
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            log.warn("Login rejected: account not found");
            auditLogin(null, username, clientIp, "LOGIN_FAIL", "账号不存在", started);
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            log.warn("Login rejected: disabled userId={}", user.getId());
            auditLogin(user.getId(), username, clientIp, "LOGIN_FAIL", "账号停用", started);
            throw new DisabledException("账号已停用");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            log.warn("Login rejected: locked userId={}", user.getId());
            auditLogin(user.getId(), username, clientIp, "LOGIN_FAIL", "账号冻结", started);
            throw new LockedException("账号已临时锁定");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            int failures = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
            user.setLoginFailCount(failures);
            if (failures >= MAX_FAILURES) user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            userMapper.updateById(user);
            log.warn("Login rejected: bad credentials userId={}, failureCount={}", user.getId(), failures);
            auditLogin(user.getId(), username, clientIp, "LOGIN_FAIL", "密码错误", started);
            throw new BadCredentialsException("用户名或密码错误");
        }

        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userMapper.updateById(user);
        userMapper.clearLoginLock(user.getId());

        permissionCacheService.evict(user.getId());
        SecuritySnapshot snapshot = identityService.load(user.getId());
        List<String> roles = permissionCacheService.roles(
                user.getId(), () -> identityMapper.selectRoleCodes(user.getId()));
        List<String> permissions = snapshot.permissions().stream().sorted().toList();
        List<SecurityIdentityMapper.SecurityMenuRow> menuRows = permissionCacheService.menus(
                user.getId(), () -> identityMapper.selectMenus(user.getId()));
        List<LoginResponse.MenuItem> menus = buildMenuTree(menuRows);
        List<String> buttonPermissions = menuRows.stream()
                .filter(menu -> "B".equals(menu.menuType()) && menu.permission() != null)
                .map(SecurityIdentityMapper.SecurityMenuRow::permission).distinct().sorted().toList();
        String token = tokenService.createToken(user.getId(), user.getTokenVersion() == null ? 0 : user.getTokenVersion());
        auditLogin(user.getId(), username, clientIp, "LOGIN_SUCCESS", null, started);
        return new LoginResponse(token,
                new LoginResponse.UserInfo(user.getId(), user.getUsername(), user.getRealName(), user.getOrgId()),
                roles, permissions, buttonPermissions, menus);
    }

    private void auditLogin(
            Long userId, String username, String clientIp, String event, String failureReason, long started) {
        auditLogService.save(new AuditLogCommand(
                userId, username, event, "AUTH", "用户登录", "/auth/login", "POST",
                null, failureReason == null ? "认证成功" : "认证失败", clientIp,
                failureReason == null ? "SUCCESS" : "FAIL", failureReason,
                Math.max(0, (System.nanoTime() - started) / 1_000_000), AuditRequestSupport.traceId()));
    }

    private List<LoginResponse.MenuItem> buildMenuTree(List<SecurityIdentityMapper.SecurityMenuRow> rows) {
        Map<Long, LoginResponse.MenuItem> nodes = new LinkedHashMap<>();
        for (SecurityIdentityMapper.SecurityMenuRow row : rows) {
            if (!"B".equals(row.menuType()) && !Integer.valueOf(0).equals(row.visible())) {
                nodes.put(row.id(), new LoginResponse.MenuItem(row.id(), row.parentId(), row.menuName(), row.menuType(),
                        row.path(), row.component(), row.permission(), row.icon(), row.visible(), new ArrayList<>()));
            }
        }
        List<LoginResponse.MenuItem> roots = new ArrayList<>();
        for (LoginResponse.MenuItem node : nodes.values()) {
            LoginResponse.MenuItem parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent == null) roots.add(node); else parent.children().add(node);
        }
        return roots;
    }
}
