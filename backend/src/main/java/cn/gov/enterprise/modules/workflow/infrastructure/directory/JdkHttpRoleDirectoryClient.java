package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Objects;

/** JDK HTTP transport implementation. Authentication is deliberately left to an outer client policy. */
public final class JdkHttpRoleDirectoryClient implements RoleDirectoryClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RoleDirectoryClientProperties properties;

    public JdkHttpRoleDirectoryClient(ObjectMapper objectMapper, RoleDirectoryClientProperties properties) {
        this(HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build(),
                objectMapper, properties);
    }

    JdkHttpRoleDirectoryClient(HttpClient httpClient, ObjectMapper objectMapper,
            RoleDirectoryClientProperties properties) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public RoleDirectoryTransportResponse fetch(RoleDirectoryQuery query) {
        try {
            HttpRequest request = HttpRequest.newBuilder(properties.endpointIdentity())
                    .timeout(properties.readTimeout()).header("Content-Type", "application/json")
                    .header("X-Correlation-Id", query.traceId())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(query)))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 500) throw new DirectoryFailure(
                    DirectoryFailure.Code.TEMPORARY_UNAVAILABLE, "directory returned server error");
            if (response.statusCode() >= 400) throw new DirectoryFailure(
                    DirectoryFailure.Code.ROLE_NOT_FOUND, "directory rejected the governed query");
            return objectMapper.readValue(response.body(), RoleDirectoryTransportResponse.class);
        } catch (HttpTimeoutException exception) {
            throw new DirectoryFailure(DirectoryFailure.Code.NETWORK_TIMEOUT,
                    "directory request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DirectoryFailure(DirectoryFailure.Code.TRANSIENT_DEPENDENCY_FAILURE,
                    "directory request interrupted", exception);
        } catch (IOException exception) {
            throw new DirectoryFailure(DirectoryFailure.Code.TEMPORARY_UNAVAILABLE,
                    "directory transport failed", exception);
        }
    }
}
