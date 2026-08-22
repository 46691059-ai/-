package cn.gov.enterprise.modules.governance.audit.interfaces;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Dedicated fail-closed service authentication. Secrets are never logged. */
@Component
public class ExternalAuditSinkAuthenticationFilter extends OncePerRequestFilter {
    private static final String PREFIX="/internal/governance-audit-sink/";
    private final ExternalAuditSinkProperties properties;
    public ExternalAuditSinkAuthenticationFilter(ExternalAuditSinkProperties properties){this.properties=properties;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().contains(PREFIX);}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if(!properties.enabled()||!properties.executableEnvironment()){reject(response,503,"AUDIT_SINK_NOT_AVAILABLE");return;}
        if(!request.isSecure()){reject(response,426,"TLS_REQUIRED");return;}
        String authorization=request.getHeader("Authorization");
        String client=request.getHeader("X-Service-Identity");
        if(authorization==null||!authorization.startsWith("Bearer ")){reject(response,401,"SERVICE_CREDENTIAL_REQUIRED");return;}
        if(!constantTime(authorization.substring(7),properties.serviceToken())){reject(response,401,"SERVICE_CREDENTIAL_INVALID");return;}
        if(!constantTime(client,properties.acceptedClientIdentity())){reject(response,403,"SERVICE_IDENTITY_FORBIDDEN");return;}
        chain.doFilter(request,response);
    }
    private static boolean constantTime(String actual,String expected){
        if(actual==null||expected==null||expected.isBlank())return false;
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8));
    }
    private static void reject(HttpServletResponse response,int status,String code)throws IOException{
        response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\""+code+"\"}");
    }
}
