package cn.gov.enterprise.modules.organization.approvalrole.interfaces.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApprovalRoleDirectoryServiceAuthenticationFilterTest {
    @Test void missingInvalidAndWrongIdentityMustFailClosed()throws Exception{
        var filter=new ApprovalRoleDirectoryServiceAuthenticationFilter(ApprovalRoleDirectoryProviderContractTest.properties(),new InMemoryApprovalRoleDirectoryProviderAuditSink());
        assertThat(call(filter,null,null,true)).isEqualTo(401);
        assertThat(call(filter,"wrong","workflow-role-directory-client",true)).isEqualTo(401);
        assertThat(call(filter,"test-only-secret-token-32-characters-minimum","wrong-client",true)).isEqualTo(403);
        assertThat(call(filter,"test-only-secret-token-32-characters-minimum","workflow-role-directory-client",false)).isEqualTo(403);
    }
    @Test void validServiceIdentityAndCredentialOverTlsPasses()throws Exception{
        var filter=new ApprovalRoleDirectoryServiceAuthenticationFilter(ApprovalRoleDirectoryProviderContractTest.properties(),new InMemoryApprovalRoleDirectoryProviderAuditSink());
        assertThat(call(filter,"test-only-secret-token-32-characters-minimum","workflow-role-directory-client",true)).isEqualTo(200);
    }
    private int call(ApprovalRoleDirectoryServiceAuthenticationFilter filter,String token,String identity,boolean secure)throws Exception{
        var request=new MockHttpServletRequest("GET","/internal/approval-role-directory/health");request.setServletPath("/internal/approval-role-directory/health");request.setSecure(secure);
        if(token!=null)request.addHeader("Authorization","Bearer "+token);if(identity!=null)request.addHeader("X-Service-Identity",identity);
        var response=new MockHttpServletResponse();FilterChain chain=mock(FilterChain.class);filter.doFilter(request,response,chain);
        return response.getStatus();
    }
}
