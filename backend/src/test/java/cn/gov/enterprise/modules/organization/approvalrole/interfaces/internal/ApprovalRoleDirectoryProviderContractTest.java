package cn.gov.enterprise.modules.organization.approvalrole.interfaces.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.gov.enterprise.modules.organization.approvalrole.application.ApprovalRoleDirectoryService;
import cn.gov.enterprise.modules.workflow.domain.role.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ApprovalRoleDirectoryProviderContractTest {
    @Test void fixedVectorMustCrossValidateWithFrozenWorkflowCanonical(){
        Instant at=Instant.parse("2026-05-31T12:00:00Z");
        var member=new RoleDirectoryMember("990101","990201","TEST_APPROVER","990001",
                Instant.parse("2026-01-01T00:00:00Z"),Instant.parse("2026-06-01T00:00:00Z"),
                RoleDirectorySourceType.MANUAL_GOVERNANCE,"VECTOR-DECISION-1",7);
        var result=RoleDirectoryResult.complete("TEST_APPROVER","990001",at,7,List.of(member),
                "5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d",
                ApprovalRoleDirectoryProviderFacade.SOURCE);
        assertThat(ApprovalRoleDirectoryProviderFacade.canonicalVectorHash()).isEqualTo(result.resultHash());
    }

    @Test void metadataMustFreezeEnvironmentCapabilitiesAndNoPagination(){
        var properties=properties();var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject("SELECT 1",Integer.class)).thenReturn(1);
        when(jdbc.queryForObject(startsWith("SELECT COUNT"),eq(Integer.class))).thenReturn(4);
        var facade=new ApprovalRoleDirectoryProviderFacade(mock(ApprovalRoleDirectoryService.class),properties,
                new InMemoryApprovalRoleDirectoryProviderAuditSink(),jdbc,new SimpleMeterRegistry());
        var metadata=facade.metadata();
        assertThat(metadata.providerCode()).isEqualTo("ORG_GOV_APPROVAL_ROLE_DIRECTORY");
        assertThat(metadata.environmentIdentity()).isEqualTo("TEST");
        assertThat(metadata.contractVersion()).isEqualTo("ROLE_DIRECTORY_PORT_V1");
        assertThat(metadata.canonicalVersion()).isEqualTo("ROLE_CANONICAL_JSON_V1");
        assertThat(metadata.paginationSupported()).isFalse();
        assertThat(metadata.supportedCapabilities()).contains("AGGREGATE_REVISION","HISTORICAL_EFFECTIVE_AT","COMPLETE_RESULT");
        assertThat(facade.health().status()).isEqualTo("UP");
    }

    static ApprovalRoleDirectoryProviderProperties properties(){
        var p=new ApprovalRoleDirectoryProviderProperties();p.setEnabled(true);p.setEnvironmentIdentity("TEST");
        p.setServiceToken("test-only-secret-token-32-characters-minimum");return p;
    }
}
