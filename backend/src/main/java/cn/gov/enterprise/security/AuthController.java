package cn.gov.enterprise.security;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.system.log.support.AuditRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final JwtTokenService tokenService;
    private final LoginService loginService;
    private final AuthRouteService routeService;
    private final CurrentSecurityContext securityContext;
    private final PermissionCacheService permissionCacheService;

    public AuthController(
            JwtTokenService tokenService,
            LoginService loginService,
            AuthRouteService routeService,
            CurrentSecurityContext securityContext,
            PermissionCacheService permissionCacheService) {
        this.tokenService = tokenService;
        this.loginService = loginService;
        this.routeService = routeService;
        this.securityContext = securityContext;
        this.permissionCacheService = permissionCacheService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @org.springframework.web.bind.annotation.RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(loginService.login(request, AuditRequestSupport.clientIp(servletRequest)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (!authorization.startsWith("Bearer ")) {
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "令牌格式无效");
        }
        tokenService.revoke(authorization.substring(7));
        permissionCacheService.evict(securityContext.userId());
        return ApiResponse.success(null);
    }

    @GetMapping("/routes")
    public ApiResponse<List<LoginResponse.MenuItem>> routes() {
        return ApiResponse.success(routeService.routes(securityContext.userId()));
    }
}
