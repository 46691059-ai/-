package cn.gov.enterprise.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentSecurityContext {
    public SecurityPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("未获取到有效登录身份");
        }
        return principal;
    }

    public Long userId() {
        return principal().userId();
    }
}
