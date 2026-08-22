package cn.gov.enterprise.modules.governance.audit;

import static org.assertj.core.api.Assertions.*;
import cn.gov.enterprise.modules.governance.audit.domain.*;
import cn.gov.enterprise.modules.workflow.infrastructure.eligibility.JdkHttpsExternalAuditSinkClient;
import cn.gov.enterprise.modules.workflow.infrastructure.eligibility.ExternalAuditDispatcher;
import cn.gov.enterprise.modules.workflow.infrastructure.eligibility.RoleRuntimeGovernanceControlStore;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@EnabledIfEnvironmentVariable(named="AUDIT_LIVE_ENDPOINT",matches="https://.+")
class ExternalAuditLiveHttpsValidationTest {
    private static final String H="a".repeat(64);
    private static ObjectMapper json; private static HttpClient trusted; private static URI endpoint;
    @BeforeAll static void setup()throws Exception{
        json=new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        endpoint=URI.create(System.getenv("AUDIT_LIVE_ENDPOINT"));
        KeyStore store=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(Path.of(System.getenv("AUDIT_TRUSTSTORE")))){store.load(in,System.getenv("AUDIT_TRUSTSTORE_PASSWORD").toCharArray());}
        TrustManagerFactory tmf=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tmf.init(store);
        SSLContext ssl=SSLContext.getInstance("TLSv1.3");ssl.init(null,tmf.getTrustManagers(),null);
        trusted=HttpClient.newBuilder().sslContext(ssl).connectTimeout(java.time.Duration.ofSeconds(2)).build();
    }
    @Test void realTlsAuthMetadataIdempotentReceiptAndPersistence()throws Exception{
        var client=new JdkHttpsExternalAuditSinkClient(trusted,json,endpoint,token(),identity(),"TEST","GOVERNANCE_AUDIT_SINK");
        assertThat(client.metadata().environmentIdentity()).isEqualTo("TEST");
        var event=event("LIVE-E1").withComputedHash();var first=client.deliver(event);var duplicate=client.deliver(event);
        assertThat(first.receiptStatus()).isEqualTo(ExternalAuditReceipt.Status.ACCEPTED);
        assertThat(first.receiptHash()).isEqualTo(duplicate.receiptHash());assertThat(first.externalReceiptId()).isEqualTo(duplicate.externalReceiptId());
        try(Connection c=DriverManager.getConnection(System.getenv("AUDIT_DB_URL"),"root","");Statement s=c.createStatement()){
            assertThat(single(s,"SELECT COUNT(*) FROM governance_external_audit_event WHERE audit_event_id='LIVE-E1'")).isEqualTo(1);
            assertThat(single(s,"SELECT COUNT(*) FROM governance_external_audit_receipt WHERE audit_event_id='LIVE-E1'")).isEqualTo(1);
        }
    }
    @Test void failureMatrixIsFailClosed()throws Exception{
        assertThatThrownBy(()->HttpClient.newHttpClient().send(HttpRequest.newBuilder(endpoint).POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString())).isInstanceOf(Exception.class);
        URI wrongHost=URI.create(endpoint.toString().replace("localhost","127.0.0.1"));
        assertThatThrownBy(()->trusted.send(HttpRequest.newBuilder(wrongHost).POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString())).isInstanceOf(Exception.class);
        var unavailable=new JdkHttpsExternalAuditSinkClient(trusted,json,URI.create("https://localhost:1/api/internal/governance-audit-sink/events"),token(),identity(),"TEST","GOVERNANCE_AUDIT_SINK");
        assertThatThrownBy(()->unavailable.deliver(event("CONNECTION-REFUSED").withComputedHash()))
                .isInstanceOf(cn.gov.enterprise.modules.workflow.infrastructure.eligibility.ExternalAuditSinkClient.DeliveryException.class);
        assertThat(status(request(event("AUTH-MISSING").withComputedHash(),null,null,null))).isEqualTo(401);
        assertThat(status(request(event("AUTH-BAD").withComputedHash(),"bad",identity(),null))).isEqualTo(401);
        assertThat(status(request(event("IDENTITY-BAD").withComputedHash(),token(),"wrong",null))).isEqualTo(403);
        assertThat(status(request(event("HTTP500").withComputedHash(),token(),identity(),"HTTP_500"))).isEqualTo(500);
        assertThat(status(request(event("HTTP503").withComputedHash(),token(),identity(),"HTTP_503"))).isEqualTo(503);
        HttpRequest timeout=HttpRequest.newBuilder(endpoint).timeout(java.time.Duration.ofMillis(500))
                .header("Content-Type","application/json").header("Authorization","Bearer "+token())
                .header("X-Service-Identity",identity()).header("X-Audit-Test-Failure","TIMEOUT")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(event("TIMEOUT").withComputedHash()))).build();
        assertThatThrownBy(()->trusted.send(timeout,HttpResponse.BodyHandlers.ofString())).isInstanceOf(java.net.http.HttpTimeoutException.class);
        assertThat(request(event("MALFORMED").withComputedHash(),token(),identity(),"MALFORMED").body()).isEqualTo("{");
        for(String mode:List.of("WRONG_EVENT","WRONG_PAYLOAD","WRONG_RECEIPT_HASH")){
            HttpResponse<String> response=request(event("BAD-"+mode).withComputedHash(),token(),identity(),mode);
            assertThatThrownBy(()->json.readValue(response.body(),ExternalAuditReceipt.class)).isInstanceOf(Exception.class);
        }
        assertThat(status(request(event("REJECTED").withComputedHash(),token(),identity(),"REJECTED"))).isEqualTo(422);
    }
    @Test void outboxDeliversReceiptWithoutHoldingClaimTransaction()throws Exception{
        var client=new JdkHttpsExternalAuditSinkClient(trusted,json,endpoint,token(),identity(),"TEST","GOVERNANCE_AUDIT_SINK");
        var event=event("OUTBOX-E1").withComputedHash();
        DriverManagerDataSource ds=new DriverManagerDataSource(System.getenv("AUDIT_DB_URL"),"root","");
        JdbcTemplate jdbc=new JdbcTemplate(ds);
        jdbc.update("""
            INSERT INTO workflow_role_external_audit_outbox
            (id,audit_event_id,claim_id,claim_audit_id,provider_code,provider_version,payload_hash,status,attempt_count,
             created_by,created_time,updated_by,updated_time,deleted,delete_token,version)
            VALUES (880121,?,880101,880111,'GOVERNANCE_AUDIT_SINK','1.0.0',?,'PENDING',0,'TEST',NOW(3),'TEST',NOW(3),0,0,0)
            """,event.auditEventId(),event.payloadHash());
        jdbc.update("INSERT INTO workflow_role_external_audit_payload(outbox_id,audit_event_id,event_payload,payload_hash,created_time) VALUES(880121,?,CAST(? AS JSON),?,NOW(3))",
                event.auditEventId(),json.writeValueAsString(event),event.payloadHash());
        var dispatcher=new ExternalAuditDispatcher(jdbc,()->880131L,client,json);
        assertThat(dispatcher.dispatch(10)).isEqualTo(1);assertThat(dispatcher.dispatch(10)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_role_external_audit_outbox WHERE id=880121",String.class)).isEqualTo("ACKNOWLEDGED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_role_external_audit_receipt WHERE outbox_id=880121",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_external_audit_event WHERE audit_event_id='OUTBOX-E1'",Integer.class)).isEqualTo(1);
    }
    @Test void recordsObservedTestLatencyPercentiles(){
        var client=new JdkHttpsExternalAuditSinkClient(trusted,json,endpoint,token(),identity(),"TEST","GOVERNANCE_AUDIT_SINK");
        long[] millis=new long[60];for(int i=0;i<millis.length;i++){long started=System.nanoTime();client.deliver(event("PERF-"+i).withComputedHash());millis[i]=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);}java.util.Arrays.sort(millis);
        long p50=millis[29],p95=millis[56],p99=millis[59];System.out.println("AUDIT_HTTPS_LATENCY_MS_P50="+p50+" P95="+p95+" P99="+p99);assertThat(p99).isLessThan(3000);
    }
    @Test void realVersionedSoDProviderObservesPassViolationIndeterminateAndDrift(){
        DriverManagerDataSource ds=new DriverManagerDataSource(System.getenv("AUDIT_DB_URL"),"root","");
        JdbcTemplate jdbc=new JdbcTemplate(ds);RoleRuntimeGovernanceControlStore store=new RoleRuntimeGovernanceControlStore(jdbc);
        insertControl(jdbc,991001,"PLATFORM_SOD","ORG_INCOMPATIBLE|E1|DEF1|ORG1|NODE1","ALLOW",1,"1".repeat(64));
        var platformV1=store.latest("PLATFORM_SOD","ORG_INCOMPATIBLE|E1|DEF1|ORG1|NODE1",Instant.now()).orElseThrow();
        assertThat(platformV1.decision()).isEqualTo("ALLOW");
        insertControl(jdbc,991002,"PLATFORM_SOD","ORG_INCOMPATIBLE|E1|DEF1|ORG1|NODE1","DENY",2,"2".repeat(64));
        var platformV2=store.latest("PLATFORM_SOD","ORG_INCOMPATIBLE|E1|DEF1|ORG1|NODE1",Instant.now()).orElseThrow();
        assertThat(platformV2.decision()).isEqualTo("DENY");assertThat(platformV2.configVersion()).isEqualTo(2);assertThat(platformV2.evidenceHash()).isNotEqualTo(platformV1.evidenceHash());
        insertControl(jdbc,991003,"BUSINESS_SOD","BUSINESS_SOD|E1|PASS","ALLOW",1,"3".repeat(64));
        insertControl(jdbc,991004,"BUSINESS_SOD","BUSINESS_SOD|E1|VIOLATION","DENY",1,"4".repeat(64));
        insertControl(jdbc,991005,"BUSINESS_SOD","BUSINESS_SOD|E1|INDETERMINATE","INDETERMINATE",1,"5".repeat(64));
        assertThat(store.latest("BUSINESS_SOD","BUSINESS_SOD|E1|PASS",Instant.now()).orElseThrow().decision()).isEqualTo("ALLOW");
        assertThat(store.latest("BUSINESS_SOD","BUSINESS_SOD|E1|VIOLATION",Instant.now()).orElseThrow().decision()).isEqualTo("DENY");
        assertThat(store.latest("BUSINESS_SOD","BUSINESS_SOD|E1|INDETERMINATE",Instant.now()).orElseThrow().decision()).isEqualTo("INDETERMINATE");
        assertThat(store.latest("BUSINESS_SOD","BUSINESS_SOD|E1|MISSING",Instant.now())).isEmpty();
    }
    @Test void concurrent10_25_50HaveOneClaimAuditOutboxAndReceipt()throws Exception{
        for(int concurrency:List.of(10,25,50))runConcurrency(concurrency,890000L+concurrency*100L);
    }
    private void runConcurrency(int concurrency,long base)throws Exception{
        DriverManagerDataSource ds=new DriverManagerDataSource(System.getenv("AUDIT_DB_URL"),"root","");JdbcTemplate jdbc=new JdbcTemplate(ds);
        long execution=base+1,task=base+2,snapshot=base+3,pool=base+4;int visit=concurrency+100;
        jdbc.update("INSERT INTO workflow_node_execution(id,execution_no,instance_id,version_id,node_id,node_code_snapshot,node_name_snapshot,visit_no,status,entered_time,activated_time,trace_id,created_by,updated_by,deleted,delete_token,version) VALUES(?,CONCAT('WNE-',?),880020,880001,880011,'AUDIT_NODE','Audit Node',?,'ACTIVE',NOW(3),NOW(3),CONCAT('trace-',?),'TEST','TEST',0,0,0)",execution,execution,visit,execution);
        jdbc.update("INSERT INTO workflow_task(id,task_no,instance_id,version_id,node_id,node_execution_id,node_code_snapshot,node_name_snapshot,task_round,participant_key,assignee_user_id,candidate_snapshot,assignment_mode,status,allowed_actions,created_by,updated_by,deleted,delete_token,version) VALUES(?,CONCAT('WFT-',?),880020,880001,880011,?,'AUDIT_NODE','Audit Node',?,CONCAT('AUDIT:',?),NULL,'{}','CANDIDATE_POOL','PENDING','APPROVE,REJECT','TEST','TEST',0,0,0)",task,task,execution,visit,visit);
        String resolvedUsers=java.util.stream.IntStream.range(0,concurrency).mapToObj(i->Long.toString(900000L+i)).collect(java.util.stream.Collectors.joining(",","[","]"));
        jdbc.update("INSERT INTO workflow_task_assignment_snapshot(id,task_id,instance_id,version_id,node_id,node_execution_id,strategy_type,target_type,target_snapshot,resolved_users,resolved_user_count,resolve_time,audit_info,trace_id,created_by,updated_by,deleted,delete_token,version) VALUES(?,?,880020,880001,880011,?,'ROLE','ROLE','{}',?,?,NOW(3),'TEST',CONCAT('trace-',?),'TEST','TEST',0,0,0)",snapshot,task,execution,resolvedUsers,concurrency,task);
        jdbc.update("INSERT INTO workflow_task_candidate_pool(id,pool_no,task_id,instance_id,version_id,node_id,node_execution_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,assignment_snapshot_id,assignment_mode,strategy_type,resolver_code,resolver_version,contract_hash,rule_hash,candidate_count,generated_time,effective_time,expires_time,pool_hash,status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version) VALUES(?,CONCAT('WCP-',?),?,880020,880001,880011,?,880030,880031,880041,?,'CANDIDATE_POOL','ROLE','ROLE_RESOLVER','ROLE_V1',REPEAT('a',64),REPEAT('b',64),?,NOW(3),NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY),REPEAT('d',64),'AVAILABLE','TEST','TEST',NOW(3),'TEST',NOW(3),0,0,NULL,0)",pool,pool,task,execution,snapshot,concurrency);
        for(int i=0;i<concurrency;i++){long member=base+100+i,user=900000+i;jdbc.update("INSERT INTO workflow_task_candidate_member(id,pool_id,task_id,instance_id,candidate_user_id,source_type,source_ref_snapshot,org_id_snapshot,position_id_snapshot,role_id_snapshot,eligibility_snapshot,eligibility_hash,sort_order,generated_time,status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version) VALUES(?,?,?,880020,?,'ROLE','ROLE:APPROVER',880,NULL,8801,'{}',REPEAT('e',64),?,NOW(3),'INCLUDED','TEST','TEST',NOW(3),'TEST',NOW(3),0,0,NULL,0)",member,pool,task,user,i+1);}
        ExecutorService executor=Executors.newFixedThreadPool(concurrency);CountDownLatch start=new CountDownLatch(1);AtomicInteger winners=new AtomicInteger();List<Future<?>> futures=new ArrayList<>();long begun=System.nanoTime();
        for(int i=0;i<concurrency;i++){int index=i;futures.add(executor.submit(()->{try{start.await();new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(status->{long user=900000+index,member=base+100+index,claim=base+500+index,audit=base+600+index,outbox=base+700+index;
                    int won=jdbc.update("UPDATE workflow_task SET assignee_user_id=?,status='CLAIMED',claimed_time=NOW(3),version=version+1 WHERE id=? AND status='PENDING' AND assignee_user_id IS NULL AND version=0",user,task);if(won==0)return;
                    jdbc.update("INSERT INTO workflow_task_claim(id,claim_no,task_id,candidate_pool_id,candidate_member_id,instance_id,node_execution_id,candidate_user_id,operator_user_id,status,claim_time,eligibility_snapshot_hash,realtime_eligibility_result,data_scope_result,sod_result,rbac_result,task_status_before,task_status_after,pool_status_before,pool_status_after,task_version_before,task_version_after,idempotency_key,trace_id,created_by,updated_by,deleted,delete_token,version) VALUES(?,CONCAT('WCL-',?),?,?,?,880020,?,?,?,'CLAIMED',NOW(3),REPEAT('e',64),'ELIGIBLE','ALLOW:SCOPED','SOD:ALLOW','ALLOW:workflow:approve','PENDING','CLAIMED','AVAILABLE','CLAIMED',0,1,CONCAT('idem-',?),CONCAT('trace-',?),'TEST','TEST',0,0,0)",claim,claim,task,pool,member,execution,user,user,claim,claim);
                    if(jdbc.update("UPDATE workflow_task_candidate_pool SET status='CLAIMED',version=version+1 WHERE id=? AND status='AVAILABLE' AND version=0",pool)!=1)throw new IllegalStateException("pool CAS");
                    jdbc.update("INSERT INTO workflow_task_claim_audit(id,event_no,task_id,candidate_pool_id,claim_id,candidate_member_id,instance_id,node_execution_id,operator_user_id,claimant_user_id,event_type,result,reason_code,frozen_eligibility_hash,realtime_eligibility_result,rbac_result,data_scope_result,sod_result,task_status_before,task_status_after,pool_status_before,pool_status_after,event_time,trace_id,idempotency_key,event_hash,created_by,updated_by,deleted,delete_token,version) VALUES(?,CONCAT('WCLA-',?),?,?,?,?,880020,?,?,?,'CLAIM','SUCCESS','CLAIMED',REPEAT('e',64),'ELIGIBLE','ALLOW','ALLOW','ALLOW','PENDING','CLAIMED','AVAILABLE','CLAIMED',NOW(3),CONCAT('trace-',?),CONCAT('idem-',?),REPEAT('a',64),'TEST','TEST',0,0,0)",audit,audit,task,pool,claim,member,execution,user,user,claim,claim);
                    ExternalAuditEvent event=new ExternalAuditEvent("CONCURRENT-"+concurrency,"ROLE_TASK_CLAIMED","1",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),"880","880020",Long.toString(execution),Long.toString(task),Long.toString(claim),Long.toString(user),"880031","ADM-TEST",Long.toString(base),"REV-1",H,"d".repeat(64),H,H,"CORR-"+concurrency,null).withComputedHash();
                    jdbc.update("INSERT INTO workflow_role_external_audit_outbox(id,audit_event_id,claim_id,claim_audit_id,provider_code,provider_version,payload_hash,status,attempt_count,created_by,created_time,updated_by,updated_time,deleted,delete_token,version) VALUES(?,?,?,?,'GOVERNANCE_AUDIT_SINK','1.0.0',?,'PENDING',0,'TEST',NOW(3),'TEST',NOW(3),0,0,0)",outbox,event.auditEventId(),claim,audit,event.payloadHash());
                    jdbc.update("INSERT INTO workflow_role_external_audit_payload(outbox_id,audit_event_id,event_payload,payload_hash,created_time) VALUES(?,?,CAST(? AS JSON),?,NOW(3))",outbox,event.auditEventId(),jsonString(event),event.payloadHash());winners.incrementAndGet();});}catch(Exception e){throw new CompletionException(e);}}));}
        start.countDown();for(Future<?> f:futures)f.get(30,TimeUnit.SECONDS);executor.shutdown();long elapsed=System.nanoTime()-begun;
        assertThat(winners.get()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_task_claim WHERE task_id=?",Integer.class,task)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_task_claim_audit WHERE task_id=? AND result='SUCCESS'",Integer.class,task)).isEqualTo(1);
        var client=new JdkHttpsExternalAuditSinkClient(trusted,json,endpoint,token(),identity(),"TEST","GOVERNANCE_AUDIT_SINK");
        AtomicLong ids=new AtomicLong(base+900);var dispatcher=new ExternalAuditDispatcher(jdbc,ids::incrementAndGet,client,json);
        if(concurrency==25){
            AtomicInteger attempts=new AtomicInteger();
            var flaky=new cn.gov.enterprise.modules.workflow.infrastructure.eligibility.ExternalAuditSinkClient(){
                public ExternalAuditMetadata metadata(){return client.metadata();}
                public ExternalAuditReceipt deliver(ExternalAuditEvent e){if(attempts.getAndIncrement()==0)throw new DeliveryException("HTTP_503",true);return client.deliver(e);}};
            dispatcher=new ExternalAuditDispatcher(jdbc,ids::incrementAndGet,flaky,json);
            assertThat(dispatcher.dispatch(10)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM workflow_role_external_audit_outbox WHERE audit_event_id=?",String.class,"CONCURRENT-25")).isEqualTo("RETRY");
            jdbc.update("UPDATE workflow_role_external_audit_outbox SET next_attempt_time=NOW(3),updated_time=NOW(3),version=version+1 WHERE audit_event_id=? AND status='RETRY'","CONCURRENT-25");
        } else if(concurrency==50){
            var terminal=new cn.gov.enterprise.modules.workflow.infrastructure.eligibility.ExternalAuditSinkClient(){
                public ExternalAuditMetadata metadata(){return client.metadata();}
                public ExternalAuditReceipt deliver(ExternalAuditEvent e){throw new DeliveryException("RECEIPT_HASH_MISMATCH",false);}};
            dispatcher=new ExternalAuditDispatcher(jdbc,ids::incrementAndGet,terminal,json);
            assertThat(dispatcher.dispatch(10)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM workflow_role_external_audit_outbox WHERE audit_event_id=?",String.class,"CONCURRENT-50")).isEqualTo("DEAD");
            assertThat(dispatcher.replayDead("CONCURRENT-50")).isEqualTo(1);
            dispatcher=new ExternalAuditDispatcher(jdbc,ids::incrementAndGet,client,json);
        }
        assertThat(dispatcher.dispatch(10)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_external_audit_event WHERE audit_event_id=?",Integer.class,"CONCURRENT-"+concurrency)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_external_audit_receipt WHERE audit_event_id=?",Integer.class,"CONCURRENT-"+concurrency)).isEqualTo(1);
        System.out.println("AUDIT_CONCURRENCY_"+concurrency+"_MS="+TimeUnit.NANOSECONDS.toMillis(elapsed));
    }
    private static HttpResponse<String> request(ExternalAuditEvent event,String token,String identity,String mode)throws Exception{
        var b=HttpRequest.newBuilder(endpoint).header("Content-Type","application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);if(identity!=null)b.header("X-Service-Identity",identity);if(mode!=null)b.header("X-Audit-Test-Failure",mode);
        return trusted.send(b.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(event))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private static int status(HttpResponse<?> r){return r.statusCode();}
    private static String jsonString(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static int single(Statement s,String sql)throws SQLException{try(ResultSet r=s.executeQuery(sql)){r.next();return r.getInt(1);}}
    private static void insertControl(JdbcTemplate jdbc,long id,String type,String scope,String decision,long version,String evidence){
        jdbc.update("""
            INSERT INTO workflow_role_runtime_governance_control
            (id,control_type,scope_type,scope_key,enterprise_id,business_rule_code,decision,config_version,effective_from,
             policy_payload_hash,evidence_hash,changed_by,approved_by,reason,created_by,created_time,updated_by,updated_time,deleted,delete_token,version)
            VALUES (?,?, 'BUSINESS_OBJECT',?,'E1','TEST_RULE',?,?,'2000-01-01 00:00:00.000',REPEAT('a',64),?,'TEST','TEST','TEST','TEST',NOW(3),'TEST',NOW(3),0,0,0)
            """,id,type,scope,decision,version,evidence);
    }
    private static String token(){return System.getenv("AUDIT_SERVICE_TOKEN");}private static String identity(){return System.getenv("AUDIT_CLIENT_IDENTITY");}
    private static ExternalAuditEvent event(String id){return new ExternalAuditEvent(id,"ROLE_TASK_CLAIMED","1",Instant.parse("2026-08-21T02:03:04Z"),
            "100","200","300","400","500","600","700","ADM-1","800","REV-1",H,H,H,H,"CORR-1",null);}
}
