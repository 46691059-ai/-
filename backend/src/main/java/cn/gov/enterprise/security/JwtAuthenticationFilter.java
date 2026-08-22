package cn.gov.enterprise.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenService tokenService;
    private final SecurityIdentityService identityService;

    public JwtAuthenticationFilter(
            JwtTokenService tokenService,
            SecurityIdentityService identityService) {
        this.tokenService = tokenService;
        this.identityService = identityService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // This internal service boundary owns an independent credential verifier. Service tokens
        // must never be parsed as end-user JWTs or enter user authentication logs.
        return request.getServletPath().startsWith("/internal/approval-role-directory/")
                || request.getServletPath().startsWith("/internal/governance-audit-sink/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null
                && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var claims = tokenService.parse(authorization.substring(7));
                if (tokenService.isRevoked(claims)) {
                    throw new org.springframework.security.authentication.BadCredentialsException(
                            "令牌已撤销");
                }
                Long userId = Long.valueOf(claims.getSubject());
                SecuritySnapshot snapshot = identityService.load(userId);
                if (snapshot.tokenVersion() != tokenService.tokenVersion(claims)) {
                    throw new org.springframework.security.authentication.BadCredentialsException(
                            "令牌版本已失效");
                }
                var authorities = snapshot.permissions().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(
                        snapshot.principal(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException exception) {
                SecurityContextHolder.clearContext();
                // 禁止记录原始Token或完整认证凭据。
                log.warn("JWT authentication rejected: type={}, method={}, path={}",
                        exception.getClass().getSimpleName(), request.getMethod(), request.getRequestURI());
            }
        }
        filterChain.doFilter(request, response);
    }
}
