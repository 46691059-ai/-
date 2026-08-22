package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JdkHttpRoleDirectoryClientIntegrationTest {
    @Test
    void unknownSensitiveTransportFieldsMustNotEnterTheWhitelistedDto() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var response = ProductionRoleDirectoryAdapterTest.response(
                ProductionRoleDirectoryAdapterTest.result(List.of(
                        ProductionRoleDirectoryAdapterTest.member("USER-001", "A-1"))));
        String json = mapper.writeValueAsString(response);
        String withPii = json.substring(0, json.length() - 1)
                + ",\"phone\":\"13800000000\",\"idCard\":\"SECRET\",\"salary\":99999}";

        var parsed = mapper.readValue(withPii, RoleDirectoryTransportResponse.class);
        assertThat(List.of(RoleDirectoryTransportResponse.class.getRecordComponents()).stream()
                .map(component -> component.getName()).toList())
                .doesNotContain("phone", "idCard", "homeAddress", "salary", "privateProfile");
        assertThat(parsed.members()).hasSize(1);
    }

    @Test
    void fakeHttpServerMustExerciseRealTransportBoundaryAndBoundedRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var domainResult = ProductionRoleDirectoryAdapterTest.result(List.of(
                ProductionRoleDirectoryAdapterTest.member("USER-002", "A-2"),
                ProductionRoleDirectoryAdapterTest.member("USER-001", "A-1")));
        byte[] body = mapper.writeValueAsBytes(ProductionRoleDirectoryAdapterTest.response(domainResult));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/roles", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (calls.getAndIncrement() == 0) {
                exchange.sendResponseHeaders(500, -1);
            } else {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            var base = ProductionRoleDirectoryAdapterTest.properties(1);
            var properties = new RoleDirectoryClientProperties(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/roles"),
                    Duration.ofSeconds(1), Duration.ofSeconds(2), 1, base.environmentIdentity(),
                    base.providerCode(), base.expectedContractVersion(), base.expectedContractHash());
            var adapter = new ProductionRoleDirectoryAdapter(
                    new JdkHttpRoleDirectoryClient(mapper, properties), properties,
                    new DirectoryCandidateLimitPolicy(10, 10),
                    new InMemoryRoleDirectoryCircuitBreaker(10), RoleDirectoryMetricsPort.noop(),
                    RoleDirectoryAuditPort.noop());

            assertThat(adapter.resolve(ProductionRoleDirectoryAdapterTest.query()).resultHash())
                    .isEqualTo(domainResult.resultHash());
            assertThat(calls).hasValue(2);
        } finally {
            server.stop(0);
        }
    }
}
