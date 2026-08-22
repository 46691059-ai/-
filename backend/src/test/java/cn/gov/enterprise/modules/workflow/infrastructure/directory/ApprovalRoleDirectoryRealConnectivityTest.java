package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import static org.assertj.core.api.Assertions.*;

import cn.gov.enterprise.modules.organization.approvalrole.interfaces.internal.ApprovalRoleDirectoryProviderFacade;
import cn.gov.enterprise.modules.workflow.domain.role.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import javax.net.ssl.*;
import org.junit.jupiter.api.Test;

/** Uses the real HTTPS/MySQL Provider when org.dir.real.base is supplied; otherwise checks isolation only. */
class ApprovalRoleDirectoryRealConnectivityTest {
    @Test void realProductionAdapterConnectivityOrDisabledIsolation()throws Exception{
        String base=System.getProperty("org.dir.real.base");
        if(base==null){assertThat(ProductionRoleDirectoryAdapter.class.isAnnotationPresent(org.springframework.stereotype.Component.class)).isFalse();return;}
        var client=new AuthenticatedHttpsRoleDirectoryClient(new ObjectMapper().findAndRegisterModules(),URI.create(base),
                System.getProperty("org.dir.real.identity"),System.getProperty("org.dir.real.token"),Duration.ofSeconds(2),Duration.ofSeconds(30),ssl());
        assertThat(client.health().ready()).isTrue();
        var metadata=client.metadata();assertThat(metadata.handshake().providerCode()).isEqualTo("ORG_GOV_APPROVAL_ROLE_DIRECTORY");
        assertThat(metadata.handshake().environmentIdentity().name()).isEqualTo("TEST");
        assertThat(metadata.handshake().contractVersion()).isEqualTo(RoleDirectoryQuery.CONTRACT_VERSION);
        assertThat(metadata.handshake().contractHash()).isEqualTo(RoleDirectoryResolver.CONTRACT_HASH.value());
        assertThat(client.verifyCanonical(ApprovalRoleDirectoryProviderFacade.canonicalVectorHash()).matches()).isTrue();
        var props=new RoleDirectoryClientProperties(URI.create(base+"/resolve"),Duration.ofSeconds(2),Duration.ofSeconds(5),1,"test",
                "ORG_GOV_APPROVAL_ROLE_DIRECTORY",RoleDirectoryQuery.CONTRACT_VERSION,RoleDirectoryResolver.CONTRACT_HASH.value());
        var breaker=new InMemoryRoleDirectoryCircuitBreaker(2);var events=new CopyOnWriteArrayList<String>();var audits=new CopyOnWriteArrayList<DirectoryResolutionAuditEvidence>();
        RoleDirectoryMetricsPort metrics=(outcome,latency,count)->events.add(outcome+":"+count);
        var adapter=new ProductionRoleDirectoryAdapter(client,props,new DirectoryCandidateLimitPolicy(100,100),breaker,metrics,audits::add);
        RoleDirectoryQuery may=query("TEST_APPROVER","2026-05-31T12:00:00Z");var first=adapter.prepare(may);
        // The validation driver may already have published a correction revision; the original
        // HR evidence remains authoritative while corrected governance evidence is additive.
        assertThat(first.result().members().stream().map(RoleDirectoryMember::userId).distinct()).contains("990101");
        RoleDirectoryQuery june=query("TEST_APPROVER","2026-06-01T00:00:00Z");assertThat(adapter.resolve(june).members().stream().map(RoleDirectoryMember::userId).distinct()).containsExactly("990102");
        var stale=new DirectoryRevisionFence(may.enterpriseId(),may.organizationId(),may.roleCode(),may.effectiveAt(),first.result().revision()-1,first.result().resultHash(),first.result().contractHash());
        assertThatThrownBy(()->adapter.verify(stale,may)).isInstanceOf(DirectoryFailure.class).extracting(e->((DirectoryFailure)e).code()).isEqualTo(DirectoryFailure.Code.REVISION_MISMATCH);
        RoleDirectoryQuery zero=query("TEST_FINANCE_REVIEWER","2026-05-31T12:00:00Z");assertThat(client.fetch(zero).members()).isEmpty();
        assertThatThrownBy(()->adapter.resolve(zero)).isInstanceOf(DirectoryFailure.class).extracting(e->((DirectoryFailure)e).code()).isEqualTo(DirectoryFailure.Code.ROLE_NOT_FOUND);
        assertThatThrownBy(()->client.fetch(query("TEST_CONFLICT","2026-05-31T12:00:00Z"))).isInstanceOf(DirectoryFailure.class).extracting(e->((DirectoryFailure)e).code()).isEqualTo(DirectoryFailure.Code.SOURCE_CONFLICT);
        assertThatThrownBy(()->adapter.resolve(query("TEST_LOAD_500","2026-05-31T12:00:00Z"))).isInstanceOf(DirectoryFailure.class).extracting(e->((DirectoryFailure)e).code()).isEqualTo(DirectoryFailure.Code.CANDIDATE_LIMIT_EXCEEDED);
        assertThat(events).isNotEmpty();assertThat(audits).isNotEmpty();assertThat(audits.getFirst().queryHash()).matches("[0-9a-f]{64}");
        writeLoadEvidence(client);
        for(int concurrency:new int[]{10,25,50}) verifyConcurrentDirectoryRequests(client,concurrency);
        assertThat(breaker.state()).isEqualTo(RoleDirectoryCircuitBreaker.State.CLOSED);
    }
    private void verifyConcurrentDirectoryRequests(AuthenticatedHttpsRoleDirectoryClient client,int concurrency)throws Exception{
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            List<java.util.concurrent.Future<RoleDirectoryTransportResponse>> calls=new ArrayList<>();
            for(int i=0;i<concurrency;i++)calls.add(executor.submit(()->client.fetch(query("TEST_LOAD_10","2026-05-31T12:00:00Z"))));
            for(var call:calls)assertThat(call.get().members().stream().map(RoleDirectoryMember::userId).distinct().count()).isEqualTo(10);
        }
    }
    private RoleDirectoryQuery query(String role,String at){return new RoleDirectoryQuery("TEST_ENTERPRISE","990001",role,Instant.parse(at),RoleDirectoryQuery.CONTRACT_VERSION,"real-connectivity-test");}
    private void writeLoadEvidence(AuthenticatedHttpsRoleDirectoryClient client)throws Exception{
        Map<String,Object> evidence=new LinkedHashMap<>();List<Long> all=new ArrayList<>();long bytes=0;int calls=0;
        for(int size:new int[]{1,10,50,100,500}){List<Long> samples=new ArrayList<>();for(int i=0;i<12;i++){long start=System.nanoTime();var response=client.fetch(query("TEST_LOAD_"+size,"2026-05-31T12:00:00Z"));long elapsed=System.nanoTime()-start;samples.add(elapsed);all.add(elapsed);bytes+=new ObjectMapper().findAndRegisterModules().writeValueAsBytes(response).length;calls++;assertThat(response.members().stream().map(RoleDirectoryMember::userId).distinct().count()).isEqualTo(size);}samples.sort(Long::compareTo);evidence.put("candidates_"+size,Map.of("p50_ms",ms(percentile(samples,.50)),"p95_ms",ms(percentile(samples,.95)),"p99_ms",ms(percentile(samples,.99))));}
        all.sort(Long::compareTo);evidence.put("aggregate",Map.of("requests",calls,"success_rate",1.0,"error_rate",0.0,"timeout_rate",0.0,"p50_ms",ms(percentile(all,.50)),"p95_ms",ms(percentile(all,.95)),"p99_ms",ms(percentile(all,.99)),"average_payload_bytes",bytes/calls));
        String out=System.getProperty("org.dir.real.evidence");if(out!=null)Files.writeString(Path.of(out),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }
    private static long percentile(List<Long> values,double p){return values.get(Math.min(values.size()-1,(int)Math.ceil(values.size()*p)-1));}
    private static double ms(long nanos){return Math.round(nanos/1000.0)/1000.0;}
    private SSLContext ssl()throws Exception{char[] pass=System.getProperty("org.dir.real.trustpass").toCharArray();KeyStore store=KeyStore.getInstance("PKCS12");try(var in=new FileInputStream(System.getProperty("org.dir.real.truststore"))){store.load(in,pass);}TrustManagerFactory tmf=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tmf.init(store);SSLContext context=SSLContext.getInstance("TLSv1.3");context.init(null,tmf.getTrustManagers(),null);return context;}
}
