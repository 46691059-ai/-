package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import cn.gov.enterprise.modules.organization.approvalrole.domain.audit.ApprovalRoleDirectoryProviderAuditEvidence;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.mapper.ApprovalRoleDirectoryProviderAuditMapper;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity.ApprovalRoleDirectoryProviderAuditEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class PersistentApprovalRoleDirectoryProviderAuditAdapterTest {
    @Test void connectionAndConstraintFailuresMustBeFailClosed(){
        var mapper=mock(ApprovalRoleDirectoryProviderAuditMapper.class);var adapter=new PersistentApprovalRoleDirectoryProviderAuditAdapter(mapper);
        when(mapper.insert(any(ApprovalRoleDirectoryProviderAuditEntity.class))).thenThrow(new DataAccessResourceFailureException("connection unavailable"));
        assertThatThrownBy(()->adapter.append(evidence())).isInstanceOf(PersistentApprovalRoleDirectoryProviderAuditAdapter.AuditPersistenceException.class).hasMessage("PROVIDER_AUDIT_PERSISTENCE_FAILED");
        verify(mapper,times(1)).insert(any(ApprovalRoleDirectoryProviderAuditEntity.class));
    }
    private ApprovalRoleDirectoryProviderAuditEvidence evidence(){Instant at=Instant.parse("2026-08-21T01:02:03Z");return ApprovalRoleDirectoryProviderAuditEvidence.create("C","R","ORG_GOV_APPROVAL_ROLE_DIRECTORY","1.0.0","provider","TEST","workflow","E","O","ROLE_A",at,"ROLE_DIRECTORY_PORT_V1","a".repeat(64),"ROLE_CANONICAL_JSON_V1",1L,"b".repeat(64),1,ApprovalRoleDirectoryProviderAuditEvidence.Outcome.SUCCESS,null,at,at.plusMillis(1));}
}
