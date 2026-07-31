package cn.gov.enterprise.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokenService;
    private final SecurityIdentityService identityService;

    public JwtAuthenticationFilter(
            JwtTokenService tokenService,
            SecurityIdentityService identityService) {
        this.tokenService = tokenService;
        this.identityService = identityService;
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
            }
        }
        filterChain.doFilter(request, response);
    }
}
