package cn.gov.enterprise.security;

import cn.gov.enterprise.common.api.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final JwtTokenService tokenService;

    public AuthController(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (!authorization.startsWith("Bearer ")) {
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "令牌格式无效");
        }
        tokenService.revoke(authorization.substring(7));
        return ApiResponse.success(null);
    }
}
