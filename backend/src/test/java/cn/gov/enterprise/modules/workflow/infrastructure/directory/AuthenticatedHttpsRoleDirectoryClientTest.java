package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

class AuthenticatedHttpsRoleDirectoryClientTest {
    @Test void httpDowngradeAndWeakCredentialAreRejected()throws Exception{
        assertThatThrownBy(()->new AuthenticatedHttpsRoleDirectoryClient(new ObjectMapper(),URI.create("http://localhost/provider"),
                "workflow-role-directory-client","x".repeat(40),Duration.ofSeconds(1),Duration.ofSeconds(1),SSLContext.getDefault()))
                .isInstanceOf(DirectoryFailure.class);
        assertThatThrownBy(()->new AuthenticatedHttpsRoleDirectoryClient(new ObjectMapper(),URI.create("https://localhost/provider"),
                "workflow-role-directory-client","short",Duration.ofSeconds(1),Duration.ofSeconds(1),SSLContext.getDefault()))
                .isInstanceOf(DirectoryFailure.class);
    }
}
