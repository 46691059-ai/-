package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class V2625RoleRuntimeActivationRealMysqlTest {
    private static final Path SQL=Path.of("..","database","migration","mysql","V2.6.25__create_role_runtime_activation_event.sql");
    private static final String H="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String C="1111111111111111111111111111111111111111";

    @Test void migrationContractContainsAuthoritativeDatabaseBarriers() throws Exception {
        String sql=Files.readString(SQL);
        assertThat(sql).contains("workflow_role_runtime_activation_event","ROLE_RUNTIME_ACTIVATED","ACTIVATE_ROLE_RUNTIME",
                "uk_role_runtime_activation_scope_type","uk_role_runtime_activation_event_id",
                "uk_role_runtime_activation_scope_sequence","uk_role_runtime_activation_scope_revision",
                "ROLE_RUNTIME_ACTIVATION_EVENT_APPEND_ONLY");
        assertThat(sql.toLowerCase()).doesNotContain("update workflow_role_canary_scope_governance","delete from workflow_role_canary_scope_governance");
    }

    @Test
    @EnabledIfEnvironmentVariable(named="V2625_MYSQL_URL",matches=".+")
    void isolatedMysqlEnforcesAppendOnlyUniquenessAndRollback() throws Exception {
        String user=System.getenv("V2625_MYSQL_USER"); String password=System.getenv("V2625_MYSQL_PASSWORD");
        if(user==null||user.isBlank()) user="root"; if(password==null) password="";
        try(Connection c=DriverManager.getConnection(System.getenv("V2625_MYSQL_URL"),user,password);Statement s=c.createStatement()){
            assertThat(s.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workflow_role_runtime_activation_event'").next()).isTrue();
            try(var rs=s.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workflow_role_runtime_activation_event'")){rs.next();assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(34);}
            seedAuthorization(s,"auth-v2625",800000L);
            s.executeUpdate(eventInsert(810000L,"evt-v2625",990404L,"auth-v2625"));
            assertThatThrownBy(()->s.executeUpdate(eventInsert(810001L,"evt-v2625-duplicate",990404L,"auth-v2625"))).isInstanceOf(SQLException.class);
            try(var rs=s.executeQuery("SELECT COUNT(*) FROM workflow_role_runtime_activation_event WHERE node_id=990404")){rs.next();assertThat(rs.getInt(1)).isEqualTo(1);}
            seedAuthorization(s,"auth-v2625-rollback",820000L);
            c.setAutoCommit(false);s.executeUpdate(eventInsert(830000L,"evt-v2625-rollback",990405L,"auth-v2625-rollback"));c.rollback();c.setAutoCommit(true);
            try(var rs=s.executeQuery("SELECT COUNT(*) FROM workflow_role_runtime_activation_event WHERE node_id=990405")){rs.next();assertThat(rs.getInt(1)).isZero();}
            assertThatThrownBy(()->s.executeUpdate("UPDATE workflow_role_runtime_activation_event SET actor_id='changed' WHERE id=810000")).isInstanceOf(SQLException.class);
        }
    }
    private static void seedAuthorization(Statement s,String auth,long base) throws SQLException{
        s.executeUpdate("INSERT INTO role_runtime_activation_request(id,activation_id,resolver_code,resolver_version,contract_hash,binding_hash,candidate_hash,directory_contract_hash,directory_revision,activation_hash,approval_evidence_hash,business_scope,effective_at,requested_by,status) VALUES("+base+",'"+auth+"','TEST','V1','"+H+"','"+H+"','"+H+"','"+H+"',1,'"+H+"','"+H+"','scope','2026-08-31 10:00:00.000','tester','PERSISTED')");
        String[] roles={"BUSINESS_OWNER","SECURITY_AUDIT","RELEASE_APPROVER"};
        for(int i=0;i<3;i++)s.executeUpdate("INSERT INTO role_runtime_activation_approval(id,activation_id,approver_type,approver_id,decision,reason,source_evidence_hash,decision_hash,activation_hash,contract_hash,binding_hash,decision_time) VALUES("+(base+10+i)+",'"+auth+"','"+roles[i]+"','tester','APPROVE','approved','"+H+"','"+String.format("%064x",base+10+i)+"','"+H+"','"+H+"','"+H+"','2026-08-31 10:00:00.000')");
        s.executeUpdate("INSERT INTO role_runtime_activation_evidence(id,approval_id,activation_id,evidence_type,evidence_hash,activation_hash,contract_hash,binding_hash,canonical_version) VALUES("+(base+20)+","+(base+10)+",'"+auth+"','ACTIVATION_APPROVAL','"+H+"','"+H+"','"+H+"','"+H+"','ROLE_RUNTIME_ACTIVATION_CANONICAL_V1')");
    }
    private static String eventInsert(long id,String event,long node,String auth){return "INSERT INTO workflow_role_runtime_activation_event(id,event_id,enterprise_id,organization_id,definition_id,definition_version_id,node_id,role_code,event_type,sequence,revision,previous_state,resulting_state,authorization_id,authorization_type,authorization_commit,observation_evidence_commit,runtime_enablement_evidence_commit,runtime_release_commit,runtime_release_tag,directory_result_hash,version_binding_hash,manifest_hash,content_hash,structural_fingerprint,actor_type,actor_id,occurred_at) VALUES("+id+",'"+event+"',990001,990101,990401,990402,"+node+",'RC1_TEST_CANARY_APPROVER','ROLE_RUNTIME_ACTIVATED',1,1,'DISABLED','ACTIVATED','"+auth+"','ACTIVATE_ROLE_RUNTIME','"+C+"','"+C+"','"+C+"','"+C+"','workflow-v1.0.0-rc2.1','"+H+"','"+H+"','"+H+"','"+H+"','"+H+"','HUMAN','tester','2026-08-31 10:00:00.000')";}
}
