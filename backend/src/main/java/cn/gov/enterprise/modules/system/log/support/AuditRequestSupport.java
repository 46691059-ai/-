package cn.gov.enterprise.modules.system.log.support;

import cn.gov.enterprise.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuditRequestSupport {
    private AuditRequestSupport() {}

    public static Long userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof SecurityPrincipal principal
                ? principal.userId() : null;
    }

    public static String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        if (authentication.getPrincipal() instanceof SecurityPrincipal principal) return principal.username();
        return authentication.isAuthenticated() ? authentication.getName() : null;
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
        return ip.length() <= 64 ? ip : ip.substring(0, 64);
    }

    public static String traceId() { return MDC.get("traceId"); }
}
