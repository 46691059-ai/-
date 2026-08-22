package cn.gov.enterprise.modules.organization.approvalrole.interfaces.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import cn.gov.enterprise.modules.organization.approvalrole.domain.audit.ApprovalRoleDirectoryProviderAuditEvidence;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleDirectoryProviderAuditPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(prefix="app.approval-role-directory.provider", name="enabled", havingValue="true")
public final class ApprovalRoleDirectoryServiceAuthenticationFilter extends OncePerRequestFilter {
    private static final String PREFIX="/internal/approval-role-directory/";
    private final ApprovalRoleDirectoryProviderProperties properties;
    private final ApprovalRoleDirectoryProviderAuditPort audit;
    public ApprovalRoleDirectoryServiceAuthenticationFilter(ApprovalRoleDirectoryProviderProperties properties,ApprovalRoleDirectoryProviderAuditPort audit){properties.validateEnabled();this.properties=properties;this.audit=audit;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getServletPath().startsWith(PREFIX);}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        Instant started=Instant.now();
        if(!request.isSecure()){rejectAudited(request,response,403,"TLS_REQUIRED",started);return;}
        String authorization=request.getHeader("Authorization");
        if(authorization==null||!authorization.startsWith("Bearer ")){rejectAudited(request,response,401,"SERVICE_CREDENTIAL_REQUIRED",started);return;}
        String identity=request.getHeader("X-Service-Identity");
        if(!constant(identity,properties.getAcceptedClientIdentity())){rejectAudited(request,response,403,"SERVICE_IDENTITY_FORBIDDEN",started);return;}
        if(!constant(authorization.substring(7),properties.getServiceToken())){rejectAudited(request,response,401,"SERVICE_CREDENTIAL_INVALID",started);return;}
        chain.doFilter(request,response);
    }
    private void rejectAudited(HttpServletRequest request,HttpServletResponse response,int status,String code,Instant started)throws IOException{
        String identity=safe(request.getHeader("X-Service-Identity"));String requestId=safe(request.getHeader("X-Request-Id"));
        if(requestId==null)requestId=UUID.randomUUID().toString();
        audit.append(ApprovalRoleDirectoryProviderAuditEvidence.create(requestId,requestId,properties.getProviderCode(),properties.getProviderVersion(),
                properties.getServiceIdentity(),properties.getEnvironmentIdentity(),identity,null,null,null,null,properties.contractVersion(),
                properties.contractHash(),properties.canonicalVersion(),null,null,null,
                ApprovalRoleDirectoryProviderAuditEvidence.Outcome.REJECTED,code,started,Instant.now()));
        reject(response,status,code);
    }
    private static String safe(String value){if(value==null||value.isBlank())return null;String v=value.trim();return v.length()<=100&&v.matches("[A-Za-z0-9_.:@/-]+")?v:null;}
    private static boolean constant(String actual,String expected){return actual!=null&&MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8));}
    private static void reject(HttpServletResponse response,int status,String code)throws IOException{response.setStatus(status);response.setContentType("application/json");response.getWriter().write("{\"code\":\""+code+"\"}");}
}
