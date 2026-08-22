package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class JdkHttpsExternalAuditSinkClientContractTest {
    @Test void rejects_plain_http_and_missing_credentials(){
        assertThatThrownBy(()->new JdkHttpsExternalAuditSinkClient(HttpClient.newHttpClient(),new ObjectMapper(),
                URI.create("http://127.0.0.1/audit"),"secret","workflow"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
        assertThatThrownBy(()->new JdkHttpsExternalAuditSinkClient(HttpClient.newHttpClient(),new ObjectMapper(),
                URI.create("https://127.0.0.1/audit")," ","workflow"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("credential");
    }
}
