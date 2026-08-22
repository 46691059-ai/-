package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.modules.governance.audit.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Objects;

/** Uses the JDK trust manager and hostname verifier; trust-all and HTTP downgrade are impossible here. */
public final class JdkHttpsExternalAuditSinkClient implements ExternalAuditSinkClient {
    private final HttpClient http; private final ObjectMapper json; private final URI events;
    private final URI metadata; private final String token; private final String serviceIdentity;
    private final String expectedEnvironment; private final String expectedProvider;
    public JdkHttpsExternalAuditSinkClient(HttpClient http,ObjectMapper json,URI endpoint,
            String token,String serviceIdentity){this(http,json,endpoint,token,serviceIdentity,"TEST","GOVERNANCE_AUDIT_SINK");}
    public JdkHttpsExternalAuditSinkClient(HttpClient http,ObjectMapper json,URI endpoint,
            String token,String serviceIdentity,String expectedEnvironment,String expectedProvider){
        this.http=Objects.requireNonNull(http);this.json=Objects.requireNonNull(json);this.events=Objects.requireNonNull(endpoint);
        if(!"https".equalsIgnoreCase(endpoint.getScheme()))throw new IllegalArgumentException("external audit endpoint must use HTTPS");
        if(token==null||token.isBlank()||serviceIdentity==null||serviceIdentity.isBlank())throw new IllegalArgumentException("external audit service credential is required");
        if(!"TEST".equals(expectedEnvironment)&&!"PREPROD".equals(expectedEnvironment))throw new IllegalArgumentException("external audit environment must be TEST or PREPROD");
        this.token=token;this.serviceIdentity=serviceIdentity;this.expectedEnvironment=expectedEnvironment;this.expectedProvider=expectedProvider;
        String value=endpoint.toString();this.metadata=URI.create(value.substring(0,value.lastIndexOf('/'))+"/metadata");
    }
    @Override public ExternalAuditMetadata metadata(){
        ExternalAuditMetadata value=send(metadata,"GET",null,ExternalAuditMetadata.class);
        if(!expectedEnvironment.equals(value.environmentIdentity())||!expectedProvider.equals(value.providerCode())
                ||!ExternalAuditEvent.CONTRACT_VERSION.equals(value.contractVersion())
                ||!ExternalAuditReceipt.CANONICAL_VERSION.equals(value.receiptContractVersion()))
            throw new DeliveryException("METADATA_CONTRACT_MISMATCH",false);
        return value;
    }
    @Override public ExternalAuditReceipt deliver(ExternalAuditEvent event){
        ExternalAuditReceipt receipt=send(events,"POST",event,ExternalAuditReceipt.class);
        if(!event.auditEventId().equals(receipt.auditEventId()))throw new DeliveryException("RECEIPT_EVENT_MISMATCH",false);
        if(!event.payloadHash().equals(receipt.payloadHash()))throw new DeliveryException("RECEIPT_PAYLOAD_MISMATCH",false);
        if(receipt.receiptStatus()!=ExternalAuditReceipt.Status.ACCEPTED)throw new DeliveryException("RECEIPT_REJECTED",false);
        if(!receipt.verify())throw new DeliveryException("RECEIPT_HASH_MISMATCH",false);
        return receipt;
    }
    private <T>T send(URI uri,String method,Object body,Class<T> type){
        try{
            HttpRequest.Builder builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3))
                    .header("Accept","application/json").header("Authorization","Bearer "+token)
                    .header("X-Service-Identity",serviceIdentity);
            if(body==null)builder.GET();else builder.header("Content-Type","application/json")
                    .header("Idempotency-Key",((ExternalAuditEvent)body).auditEventId())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
            HttpResponse<byte[]> response=http.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());
            int status=response.statusCode();
            if(status<200||status>=300)throw new DeliveryException("HTTP_"+status,status==500||status==502||status==503||status==504);
            try{return json.readValue(response.body(),type);}
            catch(Exception malformed){throw new DeliveryException("MALFORMED_RESPONSE",false,malformed);}
        }catch(DeliveryException e){throw e;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new DeliveryException("INTERRUPTED",true,e);}
        catch(java.net.http.HttpTimeoutException e){throw new DeliveryException("TIMEOUT",true,e);}
        catch(Exception e){throw new DeliveryException("CONNECTION_OR_TLS_FAILURE",true,e);}
    }
}
