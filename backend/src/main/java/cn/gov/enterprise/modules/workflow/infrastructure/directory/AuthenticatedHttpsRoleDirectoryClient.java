package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryQuery;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryContractHandshake;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryEnvironmentIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import javax.net.ssl.SSLContext;

/** Authenticated HTTPS transport for TEST/PREPROD. Default JSSE trust and hostname checks remain enabled. */
public final class AuthenticatedHttpsRoleDirectoryClient implements RoleDirectoryClient {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI baseUri;
    private final String serviceIdentity;
    private final String serviceToken;
    private final Duration readTimeout;

    public AuthenticatedHttpsRoleDirectoryClient(ObjectMapper mapper, URI baseUri,
            String serviceIdentity, String serviceToken, Duration connectTimeout,
            Duration readTimeout, SSLContext sslContext) {
        this.mapper=Objects.requireNonNull(mapper,"mapper"); this.baseUri=requireHttps(baseUri);
        this.serviceIdentity=required(serviceIdentity,"serviceIdentity");this.serviceToken=required(serviceToken,"serviceToken");
        if(serviceToken.length()<32)throw new DirectoryFailure(DirectoryFailure.Code.INVALID_CONFIGURATION,"service credential is too short");
        this.readTimeout=positive(readTimeout,"readTimeout");
        this.client=HttpClient.newBuilder().connectTimeout(positive(connectTimeout,"connectTimeout"))
                .sslContext(Objects.requireNonNull(sslContext,"sslContext")).build();
    }

    @Override public RoleDirectoryTransportResponse fetch(RoleDirectoryQuery query) {
        try { return mapper.readValue(send("resolve",mapper.writeValueAsBytes(java.util.Map.of(
                "enterpriseId",query.enterpriseId(),"organizationId",query.organizationId(),"roleCode",query.roleCode(),
                "effectiveAt",query.effectiveAt(),"correlationId",query.traceId()))).body(),RoleDirectoryTransportResponse.class); }
        catch(IOException exception){throw new DirectoryFailure(DirectoryFailure.Code.TEMPORARY_UNAVAILABLE,"directory response decoding failed",exception);}
    }

    public HealthProbe health(){
        try{JsonNode n=mapper.readTree(sendGet("health").body());return new HealthProbe(n.path("status").asText(),n.path("databaseConnected").asBoolean(),n.path("directorySchemaAvailable").asBoolean());}
        catch(IOException exception){throw new DirectoryFailure(DirectoryFailure.Code.TEMPORARY_UNAVAILABLE,"health response decoding failed",exception);}
    }

    public MetadataProbe metadata(){
        try{
            JsonNode n=mapper.readTree(sendGet("metadata").body());HashSet<String> capabilities=new HashSet<>();n.path("supportedCapabilities").forEach(v->capabilities.add(v.asText()));
            var handshake=new RoleDirectoryContractHandshake(n.path("providerCode").asText(),n.path("providerVersion").asText(),
                    RoleDirectoryEnvironmentIdentity.valueOf(n.path("environmentIdentity").asText()),n.path("contractVersion").asText(),
                    n.path("contractHash").asText(),n.path("canonicalVersion").asText(),n.path("canonicalVectorHash").asText(),capabilities);
            return new MetadataProbe(handshake,n.path("serviceIdentity").asText(),n.path("revisionSemantics").asText(),
                    n.path("historicalQuerySupported").asBoolean(),n.path("completeSemantics").asText(),n.path("paginationSupported").asBoolean());
        }catch(IOException|IllegalArgumentException exception){throw new DirectoryFailure(DirectoryFailure.Code.CONTRACT_MISMATCH,"metadata handshake is invalid",exception);}
    }

    public CanonicalProbe verifyCanonical(String expectedHash){
        try{byte[] body=mapper.writeValueAsBytes(java.util.Map.of("expectedHash",expectedHash));JsonNode n=mapper.readTree(send("canonical/verify",body).body());return new CanonicalProbe(n.path("canonicalVersion").asText(),n.path("vectorHash").asText(),n.path("matches").asBoolean());}
        catch(IOException exception){throw new DirectoryFailure(DirectoryFailure.Code.HASH_MISMATCH,"canonical verification response is invalid",exception);}
    }

    private HttpResponse<byte[]> sendGet(String path){return execute(HttpRequest.newBuilder(endpoint(path)).timeout(readTimeout).GET());}
    private HttpResponse<byte[]> send(String path,byte[] body){return execute(HttpRequest.newBuilder(endpoint(path)).timeout(readTimeout).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)));}
    private HttpResponse<byte[]> execute(HttpRequest.Builder builder){
        HttpRequest request=builder.header("Authorization","Bearer "+serviceToken).header("X-Service-Identity",serviceIdentity).build();
        try{
            HttpResponse<byte[]> response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());int status=response.statusCode();
            if(status==401||status==403)throw new DirectoryFailure(DirectoryFailure.Code.INVALID_CONFIGURATION,"directory service authentication failed");
            if(status==422)throw new DirectoryFailure(DirectoryFailure.Code.SOURCE_CONFLICT,"directory failed closed");
            if(status>=500)throw new DirectoryFailure(DirectoryFailure.Code.TEMPORARY_UNAVAILABLE,"directory returned transient server error");
            if(status>=400)throw new DirectoryFailure(DirectoryFailure.Code.ROLE_NOT_FOUND,"directory rejected query");
            return response;
        }catch(HttpTimeoutException exception){throw new DirectoryFailure(DirectoryFailure.Code.NETWORK_TIMEOUT,"directory request timed out",exception);}
        catch(InterruptedException exception){Thread.currentThread().interrupt();throw new DirectoryFailure(DirectoryFailure.Code.TRANSIENT_DEPENDENCY_FAILURE,"directory request interrupted",exception);}
        catch(IOException exception){throw new DirectoryFailure(DirectoryFailure.Code.TEMPORARY_UNAVAILABLE,"directory HTTPS transport failed",exception);}
    }
    private URI endpoint(String path){String base=baseUri.toString();if(!base.endsWith("/"))base+="/";return URI.create(base+path);}
    private static URI requireHttps(URI value){if(value==null||!"https".equalsIgnoreCase(value.getScheme())||value.getHost()==null)throw new DirectoryFailure(DirectoryFailure.Code.INVALID_CONFIGURATION,"absolute HTTPS Provider URI is required");return value;}
    private static String required(String value,String field){if(value==null||value.isBlank())throw new DirectoryFailure(DirectoryFailure.Code.INVALID_CONFIGURATION,field+" is required");return value;}
    private static Duration positive(Duration value,String field){if(value==null||value.isZero()||value.isNegative()||value.compareTo(Duration.ofSeconds(30))>0)throw new DirectoryFailure(DirectoryFailure.Code.INVALID_CONFIGURATION,field+" must be within (0,30s]");return value;}

    public record HealthProbe(String status,boolean databaseConnected,boolean directorySchemaAvailable){public boolean ready(){return "UP".equals(status)&&databaseConnected&&directorySchemaAvailable;}}
    public record MetadataProbe(RoleDirectoryContractHandshake handshake,String serviceIdentity,String revisionSemantics,boolean historicalQuerySupported,String completeSemantics,boolean paginationSupported){ }
    public record CanonicalProbe(String canonicalVersion,String vectorHash,boolean matches){ }
}
