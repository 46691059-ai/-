package cn.gov.enterprise.modules.organization.approvalrole.interfaces.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.gov.enterprise.modules.organization.approvalrole.application.ApprovalRoleDirectoryService;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleCode;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryResult;
import cn.gov.enterprise.modules.organization.approvalrole.domain.audit.ApprovalRoleDirectoryProviderAuditEvidence;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleDirectoryProviderAuditPort;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.PersistentApprovalRoleDirectoryProviderAuditAdapter.AuditPersistenceException;
import cn.gov.enterprise.modules.workflow.domain.role.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ApprovalRoleDirectoryProviderPersistentAuditContractTest {
    @Test void successMustBePersistedBeforeTrustedResultReturns(){
        var audit=new InMemoryApprovalRoleDirectoryProviderAuditSink();
        var service=mock(ApprovalRoleDirectoryService.class);
        Instant at=Instant.parse("2026-08-21T01:02:03Z");
        when(service.resolve(any())).thenReturn(ApprovalRoleDirectoryResult.complete("E",1L,new ApprovalRoleCode("ROLE_A"),at,1L,List.of(),at));
        var facade=new ApprovalRoleDirectoryProviderFacade(service,ApprovalRoleDirectoryProviderContractTest.properties(),audit,mock(JdbcTemplate.class),new SimpleMeterRegistry());
        var result=facade.resolve(new ApprovalRoleDirectoryProviderDtos.ResolveRequest("E","1","ROLE_A",at,"C-1"),"workflow","R-1");
        assertThat(result.complete()).isTrue();
        assertThat(audit.findRecent(10)).singleElement().satisfies(x->{assertThat(x.outcome()).isEqualTo(ApprovalRoleDirectoryProviderAuditEvidence.Outcome.SUCCESS);assertThat(x.requestHash()).matches("[0-9a-f]{64}");assertThat(x.evidenceHash()).matches("[0-9a-f]{64}");});
    }

    @Test void auditPersistenceFailureMustFailClosed(){
        ApprovalRoleDirectoryProviderAuditPort failing=new ApprovalRoleDirectoryProviderAuditPort(){public void append(ApprovalRoleDirectoryProviderAuditEvidence e){throw new AuditPersistenceException("DB_DOWN");}public List<ApprovalRoleDirectoryProviderAuditEvidence> findRecent(int n){return List.of();}};
        var facade=new ApprovalRoleDirectoryProviderFacade(mock(ApprovalRoleDirectoryService.class),ApprovalRoleDirectoryProviderContractTest.properties(),failing,mock(JdbcTemplate.class),new SimpleMeterRegistry());
        var request=new ApprovalRoleDirectoryProviderDtos.ResolveRequest("E","O","ROLE_A",Instant.parse("2026-08-21T01:02:03Z"),"C-1");
        assertThatThrownBy(()->facade.resolve(request,"workflow","R-1")).isInstanceOf(AuditPersistenceException.class);
    }

    @Test void canonicalMustExcludeSecretsAndBeOrderStable(){
        String hash=ApprovalRoleDirectoryProviderAuditEvidence.requestHash("workflow","E","O","ROLE_A",Instant.parse("2026-08-21T01:02:03Z"),"V1","C1");
        assertThat(hash).matches("[0-9a-f]{64}").doesNotContain("token","secret");
    }
}
